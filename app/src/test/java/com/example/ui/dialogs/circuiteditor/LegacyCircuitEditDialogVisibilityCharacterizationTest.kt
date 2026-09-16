package com.example.ui.dialogs.circuiteditor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.CircuitEntity
import com.example.ui.dialogs.CircuitEditDialog
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STAGE B0 - TEST-ONLY characterization of legacy conditional visibility,
 * legacy testTag inventory, and the panel-header gating predicate of
 * `com.example.ui.dialogs.CircuitEditDialog` (viewModel = null).
 *
 * Predicate under characterization (line 889, NOT normalized here):
 *     if (panelType != "LOAD" && panelType.isNotBlank())
 * Section init (line 138):
 *     isPanelHeaderExpanded = circuit?.panelType != null && circuit.panelType != "LOAD"
 * panelType state init (line 139):
 *     circuit?.panelType?.ifEmpty { "LOAD" } ?: "LOAD"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class LegacyCircuitEditDialogVisibilityCharacterizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(circuit: CircuitEntity? = null, existing: List<CircuitEntity> = emptyList()) {
        // The legacy dialog uses Modifier.imePadding(); its insets animation never
        // settles under Robolectric, so freeze the animation clock (state-driven
        // interactions do not need it).
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitEditDialog(
                    circuit = circuit,
                    existingCircuits = existing,
                    onDismiss = {},
                    onSave = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertTag(tag: String, expect: Boolean) {
        val interaction = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
        if (expect) interaction.assertExists() else interaction.assertDoesNotExist()
    }

    private fun assertText(text: String, expect: Boolean) {
        val interaction = composeTestRule.onNodeWithText(text, useUnmergedTree = true)
        if (expect) interaction.assertExists() else interaction.assertDoesNotExist()
    }

    private fun scrollableBoundsOrNull() =
        composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun scrollDialogBy(dy: Float) {
        val scroller = composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull() ?: return
        val wrapped = scroller.config[SemanticsActions.ScrollBy] ?: return
        wrapped.action?.invoke(0f, dy)
        composeTestRule.waitForIdle()
    }

    private fun scrollTextIntoView(text: String) {
        repeat(12) {
            val nodes = composeTestRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                val sc = scrollableBoundsOrNull()
                if (sc == null || sc.contains(nodes.first().boundsInRoot.center)) return
            }
            scrollDialogBy(260f)
        }
    }

    private fun expandSection(title: String) {
        scrollTextIntoView(title)
        // Merged tree: the clickable Row merges its title Text and the OnClick action.
        composeTestRule.onNodeWithText(title).performClick()
        composeTestRule.waitForIdle()
    }

    private fun editCircuit(panelType: String?): CircuitEntity =
        CircuitEntity(id = 8, circuitName = "X", projectId = "P", panelType = panelType)

    // ---------- Legacy testTag inventory ----------

    @Test
    fun `legacy tags - identity section is always composed in add mode`() {
        setContent()
        listOf(
            "input_circuit_name", "input_fed_from", "input_transformer_id"
        ).forEach { assertTag(it, true) }
        // Read-only selector displays the add-a-transformer placeholder (lines 290-296)
        assertText("\u2795 Add Transformer", true)
    }

    @Test
    fun `legacy tags - load section is expanded by default in add mode`() {
        setContent()
        listOf(
            "input_load_kva", "input_pf", "input_demand_factor", "input_voltage",
            "input_safety_factor", "checkbox_is_harmonic"
        ).forEach { assertTag(it, true) }
    }

    @Test
    fun `legacy tags - cable section appears after expanding its accordion`() {
        setContent()
        expandSection("3. Cable Preferences")
        listOf(
            "input_feeder_type", "input_manufacturer", "input_core_config", "input_cable_type",
            "filter_mat_Copper", "filter_mat_Aluminium",
            "filter_armour_Unarmoured", "filter_armour_Armoured",
            "input_parallel_runs", "input_length", "input_max_vd"
        ).forEach { assertTag(it, true) }
        // Busway selectors are conditional on feederType == "Busway" (line 553)
        assertTag("input_busway_mfg", false)
        assertTag("input_busway_model", false)
    }

    @Test
    fun `legacy tags - installation section appears after expanding its accordion`() {
        setContent()
        expandSection("4. Installation Conditions")
        listOf(
            "input_air_temp", "input_gnd_temp", "input_burial_depth",
            "input_soil_res", "input_no_circuits"
        ).forEach { assertTag(it, true) }
    }

    @Test
    fun `legacy tags - section titles use the legacy numbered headings`() {
        setContent()
        assertText("1. Identity & Source", true)
        assertText("2. Load Specifications", true)
        assertText("3. Cable Preferences", true)
        assertText("4. Installation Conditions", true)
        assertText("5. Panel Header Equipment Data", false) // gated by panelType (line 889)
    }

    // ---------- Transformer selector conditionality ----------

    @Test
    fun `transformer selector - hidden when fed from a parent board`() {
        val parent = listOf(
            CircuitEntity(id = 5, circuitName = "MDB-1", panelType = "MDB", projectId = "P")
        )
        setContent(
            CircuitEntity(id = 6, circuitName = "C1", projectId = "P", fedFrom = "MDB-1"),
            parent
        )
        assertTag("input_transformer_id", false)
        assertText("\u2795 Add Transformer", false)
    }

    // ---------- Panel-header gating predicate (line 889) ----------

    @Test
    fun `panel header predicate - null panelType is treated as LOAD (hidden)`() {
        setContent(editCircuit(null))
        assertTag("display_edit_dialog_sc_rating", false)
        assertText("5. Panel Header Equipment Data", false)
    }

    @Test
    fun `panel header predicate - LOAD panelType is hidden`() {
        setContent(editCircuit("LOAD"))
        assertTag("display_edit_dialog_sc_rating", false)
    }

    @Test
    fun `panel header predicate - blank panelType string is normalized to LOAD and hidden`() {
        // State init: circuit?.panelType?.ifEmpty { "LOAD" } (line 139) -> "" never reaches the predicate
        setContent(editCircuit(""))
        assertTag("display_edit_dialog_sc_rating", false)
    }

    @Test
    fun `panel header predicate - whitespace-only panelType stays hidden (legacy quirk)`() {
        setContent(editCircuit("  "))
        assertTag("display_edit_dialog_sc_rating", false)
    }

    @Test
    fun `panel header predicate - lowercase load shows section (case-sensitive legacy quirk)`() {
        setContent(editCircuit("load"))
        assertTag("display_edit_dialog_sc_rating", true)
    }

    @Test
    fun `panel header predicate - padded MDB shows section`() {
        setContent(editCircuit(" MDB "))
        assertTag("display_edit_dialog_sc_rating", true)
    }

    @Test
    fun `panel header predicate - unknown value SPARE-7 shows section`() {
        setContent(editCircuit("SPARE-7"))
        assertTag("display_edit_dialog_sc_rating", true)
    }

    @Test
    fun `panel header - full field inventory visible and expanded for MDB circuit`() {
        setContent(editCircuit("MDB")) // init expanded: panelType != null && != "LOAD" (line 138)
        listOf(
            "input_edit_dialog_busbar_rating", "display_edit_dialog_sc_rating",
            "input_edit_dialog_main_incoming_type", "input_edit_dialog_main_incoming_count",
            "input_edit_dialog_ip_rating", "input_edit_dialog_cable_entry",
            "input_edit_dialog_location", "input_edit_dialog_number_of_ways",
            "input_edit_dialog_feeder_description"
        ).forEach { assertTag(it, true) }
    }

    @Test
    fun `panel header - sc rating placeholder text with no viewmodel`() {
        setContent(editCircuit("MDB"))
        val node = composeTestRule.onNodeWithTag("display_edit_dialog_sc_rating", useUnmergedTree = true)
            .fetchSemanticsNode()
        val displayed = node.config[SemanticsProperties.EditableText].text
        assertEquals("N/A (Calculated after save)", displayed)
    }

    // ---------- Supply conditional visibility (lines 477 / 498) ----------

    @Test
    fun `supply conditionals - NORMAL hides all assigned source fields`() {
        setContent()
        assertText("Assigned ATS Source", false)
        assertText("Assigned Generator Source", false)
        assertText("Assigned UPS Source", false)
    }

    @Test
    fun `supply conditionals - EMERGENCY shows assigned ATS and Generator only`() {
        setContent(
            CircuitEntity(id = 1, circuitName = "E", projectId = "P", generatorIncluded = true)
        )
        assertText("Assigned ATS Source", true)
        assertText("Assigned Generator Source", true)
        assertText("Assigned UPS Source", false)
    }

    @Test
    fun `supply conditionals - UPS shows assigned UPS only`() {
        setContent(
            CircuitEntity(id = 1, circuitName = "U", projectId = "P", upsBacked = true)
        )
        assertText("Assigned ATS Source", false)
        assertText("Assigned Generator Source", false)
        assertText("Assigned UPS Source", true)
    }

    @Test
    fun `supply conditionals - EMERGENCY + UPS shows all three`() {
        setContent(
            CircuitEntity(
                id = 1, circuitName = "EU", projectId = "P",
                generatorIncluded = true, upsBacked = true
            )
        )
        assertText("Assigned ATS Source", true)
        assertText("Assigned Generator Source", true)
        assertText("Assigned UPS Source", true)
    }

    // ---------- Motor conditional visibility ----------

    @Test
    fun `motor fields - hidden for Static default in add mode`() {
        setContent()
        assertText("Starting Method", false)
        assertText("Start Current Multiplier", false)
    }

    @Test
    fun `motor fields - shown for Motor circuit in edit mode`() {
        setContent(
            CircuitEntity(
                id = 2, circuitName = "M", projectId = "P",
                loadNature = "Motor", startingMethod = "VFD", startingCurrentMultiplier = 6.5
            )
        )
        assertText("Starting Method", true)
        assertText("Start Current Multiplier", true)
    }

    // ---------- Computed hierarchy depth badge ----------

    @Test
    fun `computed level badge - root circuit shows Level 0 Root`() {
        setContent()
        assertText("Level 0 (Root)", true)
    }

    @Test
    fun `computed level badge - child of level-1 parent shows Level 2`() {
        val parent = listOf(
            CircuitEntity(id = 5, circuitName = "MDB-1", panelType = "MDB", level = 1, projectId = "P")
        )
        setContent(
            CircuitEntity(id = 6, circuitName = "C", projectId = "P", fedFrom = "MDB-1"),
            parent
        )
        assertText("Level 2", true)
        assertText("Level 0 (Root)", false)
    }
}
