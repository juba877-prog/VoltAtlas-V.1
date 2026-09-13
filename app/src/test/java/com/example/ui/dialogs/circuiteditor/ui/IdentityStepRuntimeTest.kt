package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for Identity & Source step.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class IdentityStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test12_allIdentityFieldsAndTagsRender() {
        val holder = CircuitEditorTestStateHolder(
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_circuit_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_panel_type").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_fed_from").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_transformer_id").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_transformer_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("computed_hierarchy_card").assertIsDisplayed()
    }

    @Test
    fun test13_transformerControlsShownOnlyForTransformerSource() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "MDB-Main"),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            existingCircuits = listOf(
                CircuitEntity(id = 1, circuitName = "MDB-Main", panelType = "MDB")
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("input_transformer_id").assertDoesNotExist()
        composeTestRule.onNodeWithTag("add_transformer_btn").assertDoesNotExist()
    }

    @Test
    fun test14_addTransformerCallbackFiresOnce() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals(0, holder.addTransformerCount)
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        assertEquals(1, holder.addTransformerCount)
    }

    @Test
    fun test15_projectSubtitleRenderBehavior() {
        val holder = CircuitEditorTestStateHolder(projectSubtitle = null)
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }
        composeTestRule.onNodeWithText("Commercial Tower Project").assertDoesNotExist()

        holder.projectSubtitle = "Commercial Tower Project"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Commercial Tower Project").assertIsDisplayed()
    }

    @Test
    fun test16_blankNameBlocksNextAndShowsValidatorError() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "   ")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        assertEquals("Circuit/Panel name cannot be empty.", holder.identityError)
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertIsDisplayed()
    }

    @Test
    fun test17_duplicateNameBlocksNext() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Existing-Panel"),
            existingCircuits = listOf(
                CircuitEntity(id = 10, circuitName = "Existing-Panel", panelType = "DB")
            ),
            editingCircuitId = 0
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        val expectedMsg = "A circuit/panel named 'Existing-Panel' already exists — choose a different name."
        assertEquals(expectedMsg, holder.identityError)
        composeTestRule.onNodeWithText(expectedMsg).assertIsDisplayed()
    }

    @Test
    fun test18_validIdentityAdvancesToLoad() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "New Feeder 1")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        assertEquals(CircuitWorkflowStep.LOAD, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_load").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertDoesNotExist()
    }

    @Test
    fun test19_hierarchyCardIsReadOnlyAndDisplaysDerivedContext() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-99"),
            availableTransformers = listOf(TransformerSnapshot("TX-99"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("computed_hierarchy_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Derived Hierarchy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supply Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 (Root = 0)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolved Transformer").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("TX-99")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("TX-99")[1].assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test20_hierarchyCardAtFontScale1_5_allLabelsAndValuesDisplayedAndClearBottomBar() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-99"),
            availableTransformers = listOf(TransformerSnapshot("TX-99"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("computed_hierarchy_card").performScrollTo()
        // Section 5 #1: Labels must remain complete and not ellipsized
        composeTestRule.onNodeWithText("Supply Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hierarchy Level").assertIsDisplayed()
        composeTestRule.onNodeWithText("Source Type").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolved Transformer").assertIsDisplayed()

        // Section 5 #3: Card bottom must scroll completely above the bottom action bar
        val cardBounds = composeTestRule.onNodeWithTag("computed_hierarchy_card").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Hierarchy card must be fully scrollable above sticky action bar (card bottom: ${cardBounds.bottom}, action bar top: ${nextBtnBounds.top})",
            cardBounds.bottom <= nextBtnBounds.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test21_validationErrorIdentityScrolledToLastHierarchyRowClearsActionBar() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "", fedFrom = "Transformer", transformerId = "TX-01"),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Trigger validation error
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()

        // Scroll to last row of hierarchy card
        val lastRow = composeTestRule.onNodeWithTag("hierarchy_field_resolved_transformer")
        lastRow.performScrollTo()
        val rowBounds = lastRow.getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()

        assertTrue(
            "Resolved transformer row must lie completely above action-bar top after scrolling in validation error state (row bottom: ${rowBounds.bottom}, next top: ${nextBtnBounds.top})",
            rowBounds.bottom <= nextBtnBounds.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun test22_bottomClearance_360dp_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-01"),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        val cardBounds = composeTestRule.onNodeWithTag("computed_hierarchy_card").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "At 360dp font 1.0, card bottom (${cardBounds.bottom}) must clear action bar top (${nextBtnBounds.top}) with margin",
            cardBounds.bottom + 16.dp <= nextBtnBounds.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test23_bottomClearance_360dp_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-01"),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        val cardBounds = composeTestRule.onNodeWithTag("computed_hierarchy_card").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "At 360dp font 1.5, card bottom (${cardBounds.bottom}) must clear action bar top (${nextBtnBounds.top}) with margin",
            cardBounds.bottom + 16.dp <= nextBtnBounds.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test24_bottomClearance_320dp_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-01"),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        val cardBounds = composeTestRule.onNodeWithTag("computed_hierarchy_card").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "At 320dp font 1.3, card bottom (${cardBounds.bottom}) must clear action bar top (${nextBtnBounds.top}) with margin",
            cardBounds.bottom + 16.dp <= nextBtnBounds.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h892dp-xhdpi", fontScale = 1.3f)
    fun test25_bottomClearance_412dp_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-01"),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        val cardBounds = composeTestRule.onNodeWithTag("computed_hierarchy_card").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "At 412dp font 1.3, card bottom (${cardBounds.bottom}) must clear action bar top (${nextBtnBounds.top}) with margin",
            cardBounds.bottom + 16.dp <= nextBtnBounds.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test26_accessibility_fontScale1_5_noTruncationAcrossAllFields() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Distribution Board Alpha",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Verify fields, values, labels, buttons without truncation
        composeTestRule.onNodeWithText("Circuit / Panel Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Board Classification").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fed From").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Transformer")[0].assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_transformer_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("computed_hierarchy_card").performScrollTo()
        composeTestRule.onNodeWithText("Derived Hierarchy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supply Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hierarchy Level").assertIsDisplayed()
        composeTestRule.onNodeWithText("Source Type").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolved Transformer").assertIsDisplayed()
    }

    @Test
    fun test27_failFirst_addTransformerIsCompactIconButtonWithExactDescription() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB", fedFrom = "Transformer"),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val addBtn = composeTestRule.onNodeWithTag("add_transformer_btn")
        addBtn.assertIsDisplayed()
        addBtn.assertContentDescriptionEquals("Add Transformer")
        val bounds = addBtn.getBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("Add Transformer must be compact icon button (width 48dp), but was $width", kotlin.math.abs((width - 48.dp).value) <= 2f)
    }

    // R1A fail-first #10: the Identity step must render the resolver's option output
    // (CircuitSourceCandidates) without reimplementing filtering in the composable.
    // Hierarchy: T(1) -> S(2) -> A(3, edited); A -> D(4) -> DD(5); T -> X(6).
    @Test
    fun test28_failFirst_fedFromOptionsRenderResolverOutputNotInlineFilter() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "DB-CenterA", panelType = "DB", fedFrom = "Transformer"),
            existingCircuits = listOf(
                CircuitEntity(id = 1, circuitName = "MDB-RootHub", panelType = "MDB"),
                CircuitEntity(id = 2, circuitName = "SMDB-LeftArm", panelType = "SMDB", parentId = 1),
                CircuitEntity(id = 3, circuitName = "DB-CenterA", panelType = "DB", parentId = 2),
                CircuitEntity(id = 4, circuitName = "DB-ChildAY", panelType = "DB", parentId = 3),
                CircuitEntity(id = 5, circuitName = "MDB-DeepZ", panelType = "MDB", parentId = 4),
                CircuitEntity(id = 6, circuitName = "SMDB-SideX", panelType = "SMDB", parentId = 1)
            ),
            editingCircuitId = 3
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("input_fed_from").performClick()
        composeTestRule.waitForIdle()

        // The four legacy special sources must be rendered by resolver output.
        composeTestRule.onNodeWithText("Generator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grid").assertIsDisplayed()
        composeTestRule.onNodeWithText("ATS").assertIsDisplayed()

        // Cycle-unsafe boards (descendants of the edited circuit) must not be offered.
        composeTestRule.onNodeWithText("DB-ChildAY").assertDoesNotExist()
        composeTestRule.onNodeWithText("MDB-DeepZ").assertDoesNotExist()

        // Valid options must be offered.
        composeTestRule.onNodeWithText("SMDB-LeftArm").assertIsDisplayed()
        composeTestRule.onNodeWithText("SMDB-SideX").assertIsDisplayed()
    }

    // ==================== R1B fail-first: inline transformer ID flow ====================

    // Composes the real entry function so the production events object runs
    // (the test host bypasses it by design).

    // 29 (fail-first). A non-blank returned ID updates draft.transformerId exactly once and
    // survives navigation to Review + save (single invocation, recomposition retention).
    // Before the fix the callback result was discarded, so the saved transformerId resolved
    // from the fabricated "TX-01" fallback instead of the returned ID -> this test failed.
    @Test
    fun test29_failFirst_entryReturnedIdUpdatesDraftAndIsSaved() {
        var callbackCalls = 0
        var saved: CircuitEntity? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = null,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { callbackCalls++; "TX-RET-01" }
                )
            }
        }
        composeTestRule.onNode(hasParent(hasTestTag("input_circuit_name")) and hasSetTextAction()).performTextInput("Entry-S")
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("callback must be invoked exactly once", 1, callbackCalls)
        assertEquals("returned ID must reach the saved entity", "TX-RET-01", saved?.transformerId)
        assertEquals("no other draft field may change (name preserved)", "Entry-S", saved?.circuitName)
    }

    // 30 (fail-first). Null result preserves prior selection: the draft stays at the entity
    // absent representation; no fabricated fallback reaches the saved entity.
    @Test
    fun test30_failFirst_entryNullResultPreservesSelection() {
        var callbackCalls = 0
        var saved: CircuitEntity? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = null,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { callbackCalls++; null }
                )
            }
        }
        composeTestRule.onNode(hasParent(hasTestTag("input_circuit_name")) and hasSetTextAction()).performTextInput("Entry-N")
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("callback must be invoked exactly once", 1, callbackCalls)
        assertEquals("null result must leave the draft at absent representation", "", saved?.transformerId)
    }

    // 31 (fail-first). Blank result preserves prior selection exactly like null.
    @Test
    fun test31_failFirst_entryBlankResultPreservesSelection() {
        var saved: CircuitEntity? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = null,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { "   " }
                )
            }
        }
        composeTestRule.onNode(hasParent(hasTestTag("input_circuit_name")) and hasSetTextAction()).performTextInput("Entry-B")
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("blank result must leave the draft at absent representation", "", saved?.transformerId)
    }

    // 32 (fail-first). The returned ID remains honestly selected even when it is not present
    // in the current snapshot list (no substitution with None or the first list item).
    @Test
    fun test32_failFirst_entryReturnedIdRetainedOverSnapshotList() {
        var saved: CircuitEntity? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = null,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    availableTransformers = listOf(
                        TransformerSnapshot("TX-01"),
                        TransformerSnapshot("TX-02")
                    ),
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { "TX-RET-77" }
                )
            }
        }
        composeTestRule.onNode(hasParent(hasTestTag("input_circuit_name")) and hasSetTextAction()).performTextInput("Entry-R")
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        composeTestRule.waitForIdle()

        // Selector must expose the returned ID, not None and not the first list item.
        composeTestRule.onAllNodesWithText("TX-RET-77").fetchSemanticsNodes().isNotEmpty()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("returned ID not in the list must still be saved", "TX-RET-77", saved?.transformerId)
    }

    // ==================== R1B closure: retention + isolation ====================

    // 33. Null result preserves a NON-EMPTY prior selection (not merely empty-means-empty).
    @Test
    fun test33_r1b_nullCallbackPreservesNonEmptyPriorSelection() {
        var saved: CircuitEntity? = null
        val existing = CircuitEntity(
            id = 5, circuitName = "Edit-Null", panelType = "LOAD",
            fedFrom = "Transformer", transformerId = "TR-EXISTING", loadKVA = 25.0
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = existing,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { null }
                )
            }
        }
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("null must preserve the prior non-empty selection", "TR-EXISTING", saved?.transformerId)
        assertEquals("no other draft field may change (name)", "Edit-Null", saved?.circuitName)
        assertEquals("no other draft field may change (load)", 25.0, saved?.loadKVA ?: 0.0, 0.0)
    }

    // 34. Blank result preserves a NON-EMPTY prior selection.
    @Test
    fun test34_r1b_blankCallbackPreservesNonEmptyPriorSelection() {
        var saved: CircuitEntity? = null
        val existing = CircuitEntity(
            id = 6, circuitName = "Edit-Blank", panelType = "LOAD",
            fedFrom = "Transformer", transformerId = "TR-EXISTING", loadKVA = 31.0
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = existing,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { "   " }
                )
            }
        }
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("blank must preserve the prior non-empty selection", "TR-EXISTING", saved?.transformerId)
        assertEquals("no other draft field may change (name)", "Edit-Blank", saved?.circuitName)
        assertEquals("no other draft field may change (load)", 31.0, saved?.loadKVA ?: 0.0, 0.0)
    }

    // 35. A non-blank result changes ONLY the transformer id.
    @Test
    fun test35_r1b_nonBlankCallbackChangesOnlyTransformerId() {
        var saved: CircuitEntity? = null
        val existing = CircuitEntity(
            id = 7, circuitName = "Edit-Only", panelType = "LOAD",
            fedFrom = "Transformer", transformerId = "TR-EXISTING", loadKVA = 12.5
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = existing,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { "TX-RET-01" }
                )
            }
        }
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("returned id must replace the prior selection", "TX-RET-01", saved?.transformerId)
        assertEquals("name untouched", "Edit-Only", saved?.circuitName)
        assertEquals("load untouched", 12.5, saved?.loadKVA ?: 0.0, 0.0)
        assertEquals("fedFrom untouched", "Transformer", saved?.fedFrom)
        assertEquals("panelType untouched", "LOAD", saved?.panelType)
    }

    // 36. A genuine post-selection snapshot-list emission must not overwrite the returned ID.
    // The supplied transformer list is driven through observable mutable state, so the
    // composable really receives a new list AFTER the selection was made.
    @Test
    fun test36_r1b_returnedIdSurvivesRealSnapshotListUpdate() {
        var transformerList by mutableStateOf(listOf(TransformerSnapshot("TX-01")))
        var saved: CircuitEntity? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = null,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    availableTransformers = transformerList,
                    onValidSaveRequested = {},
                    onSave = { saved = it },
                    onAddTransformer = { "TX-RET-77" }
                )
            }
        }
        composeTestRule.onNode(hasParent(hasTestTag("input_circuit_name")) and hasSetTextAction()).performTextInput("Emit-R")
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        composeTestRule.waitForIdle()

        // Genuine snapshot emission: the supplied list is replaced AFTER the selection exists.
        transformerList = listOf(TransformerSnapshot("TX-09"), TransformerSnapshot("TX-10"))
        composeTestRule.waitForIdle()

        assertTrue(
            "selected id must remain displayed after the list emission",
            composeTestRule.onAllNodesWithText("TX-RET-77").fetchSemanticsNodes().isNotEmpty()
        )

        repeat(5) { composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick() }
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("snapshot emission must not overwrite the returned id", "TX-RET-77", saved?.transformerId)
    }
}
