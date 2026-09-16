package com.example.ui.dialogs.circuiteditor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.example.data.CircuitEntity
import com.example.ui.dialogs.CircuitEditDialog
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STAGE B0 - TEST-ONLY characterization of the LEGACY production dialog
 * `com.example.ui.dialogs.CircuitEditDialog` driven with `viewModel = null`.
 *
 * Evidence anchors (source inspected, CircuitEditDialog.kt):
 *  - viewModel-null fallbacks: 46-61 (400 / 5.0 / Cu / XLPE / 4C, empty flows)
 *  - name trim + copy(): 1098-1099, 1118;  add constructor: 1125-1183
 *  - validation order name -> duplicate -> length: 1017-1041
 *  - coercions: 1044-1060
 *  - supply flags: 1062-1063;  finalTxId fallback: 1065-1074
 *  - hierarchy recompute: 1076-1088
 *  - unmanaged fields preserved by copy(): 1090-1117
 *  - panel header gating predicate: 889
 *
 * UNOBSERVABLE with viewModel = null (explicit gaps, see B0 report):
 *  - real addNewTransformerInline ViewModel side effect (1069),
 *  - ViewModel project stamping, Room persistence,
 *  - call-site overrideCableSize invalidation (CircuitsGridScreen.kt:391),
 *  - Flow-driven generators/ATS/UPS/transformer/short-circuit data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class LegacyCircuitEditDialogSaveMappingCharacterizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val saved = mutableListOf<CircuitEntity>()
    private var dismissCount = 0

    private fun setContent(circuit: CircuitEntity? = null, existing: List<CircuitEntity> = emptyList()) {
        saved.clear()
        dismissCount = 0
        // The legacy dialog uses Modifier.imePadding(); its insets animation never
        // settles under Robolectric, so freeze the animation clock (state-driven
        // interactions do not need it).
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitEditDialog(
                    circuit = circuit,
                    existingCircuits = existing,
                    onDismiss = { dismissCount++ },
                    onSave = { saved.add(it) }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun entity(): CircuitEntity = saved.single()

    private fun scrollableBoundsOrNull(): Rect? {
        val nodes = composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.firstOrNull()?.boundsInRoot
    }

    private fun scrollDialogBy(dy: Float) {
        val nodes = composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        val scroller = nodes.firstOrNull() ?: return
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

    private fun editableNodes() =
        composeTestRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes()

    /** Tags live on wrapper Columns; locate the editable descendant by bounds. */
    private fun setFieldText(tag: String, text: String) {
        val container = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val b = container.boundsInRoot
        val target = editableNodes().first {
            val c = it.boundsInRoot.center
            b.left - 2f <= c.x && c.x <= b.right + 2f && b.top - 2f <= c.y && c.y <= b.bottom + 2f
        }
        val action = target.config[SemanticsActions.SetText]?.action
        check(action != null) { "No SetText action under tag $tag" }
        check(action.invoke(AnnotatedString(text))) { "SetText rejected for $tag" }
        composeTestRule.waitForIdle()
    }

    /** Field without a testTag: the editable sits directly below its own label. */
    private fun setFieldTextBelowLabel(label: String, text: String) {
        val lb = composeTestRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val target = editableNodes().first {
            val c = it.boundsInRoot.center
            lb.left - 2f <= c.x && c.x <= lb.right + 2f && c.y > lb.bottom && c.y - lb.bottom < 300f
        }
        val action = target.config[SemanticsActions.SetText]?.action
        check(action != null) { "No SetText action below label $label" }
        check(action.invoke(AnnotatedString(text)))
        composeTestRule.waitForIdle()
    }

    /** Opens an ExposedDropdownMenu by clicking its merged anchor node (matched by label),
     *  then clicks the option row inside the popup. */
    private fun selectDropdown(label: String, option: String) {
        scrollTextIntoView(label)
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.waitForIdle()
        // run the menu open animation forward (clock is frozen)
        composeTestRule.mainClock.advanceTimeBy(700)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(option, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    private fun save() {
        composeTestRule.onNodeWithTag("save_circuit_btn", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    // ---------- Add-mode defaults (viewModel = null) ----------

    @Test
    fun `add mode - defaults characterize full entity`() {
        setContent()
        setFieldText("input_circuit_name", "L1")
        save()
        assertEquals(1, saved.size)
        val e = entity()
        assertEquals("L1", e.circuitName)
        assertEquals(0, e.id)
        assertEquals(10.0, e.loadKVA, 0.0)
        assertEquals("Transformer", e.fedFrom)
        assertEquals("TX-01", e.transformerId) // line 1069: no VM -> literal fallback
        assertEquals("NORMAL", e.loadType)
        assertEquals("3P+N", e.phases)
        assertEquals(400, e.voltageV)
        assertEquals(0.85, e.pf, 1e-12)
        assertEquals(1.0, e.safetyFactor, 0.0)
        assertEquals("Elsewedy", e.manufacturer)
        // resolveCableType with empty Catalog -> final literal fallback (Models.kt:500-502)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", e.cableType)
        assertEquals("Laid in Ducts", e.installation)
        assertEquals(1, e.parallelRuns)
        assertEquals(40, e.airTempC)
        assertEquals(40, e.gndTempC)
        assertEquals(0.6, e.burialDepth_m, 0.0)
        assertEquals(125, e.soilRes)
        assertEquals(1, e.noCircuits)
        assertEquals("Flat", e.formation)
        assertEquals("Touching", e.spacing)
        assertEquals(30.0, e.length_m, 0.0)
        assertEquals(0.05, e.maxVDpct, 1e-12) // defaultMaxVdPower 5.0 / 100
        assertFalse(e.generatorIncluded)
        assertFalse(e.atsIncluded)
        assertFalse(e.upsBacked)
        assertNull(e.assignedGeneratorId)
        assertNull(e.assignedAtsId)
        assertNull(e.assignedUpsId)
        assertFalse(e.isHarmonic)
        assertEquals("Static", e.loadNature)
        assertNull(e.startingMethod)
        assertNull(e.startingCurrentMultiplier)
        assertEquals("LOAD", e.panelType)
        assertNull(e.parentId)
        assertEquals(0, e.level)
        assertEquals("TRANSFORMER", e.sourceType)
        assertEquals(1.0, e.demandFactor, 0.0)
        assertNull(e.loadCategory)
        assertEquals("Cable", e.feederType)
        assertNull(e.buswayMfg)
        assertNull(e.buswayModel)
        assertEquals("DEFAULT_PROJECT", e.projectId)
        assertTrue(e.active)
    }

    @Test
    fun `name is trimmed on save`() {
        setContent()
        setFieldText("input_circuit_name", "  K-1  ")
        save()
        assertEquals("K-1", entity().circuitName)
    }

    // ---------- Validation (order: name -> duplicate -> length) ----------

    @Test
    fun `validation - empty name rejected and dialog stays open`() {
        setContent()
        save()
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertExists()
        assertEquals(0, saved.size)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `validation - duplicate name rejected case-insensitively with exact legacy message`() {
        val existing = listOf(CircuitEntity(id = 9, circuitName = "Board A", projectId = "P"))
        setContent(existing = existing)
        setFieldText("input_circuit_name", "board a")
        save()
        composeTestRule.onNodeWithText(
            "A circuit/panel named 'board a' already exists \u2014 choose a different name."
        ).assertExists()
        assertEquals(0, saved.size)
    }

    @Test
    fun `validation - edit mode excludes itself from duplicate check`() {
        val circuit = CircuitEntity(id = 9, circuitName = "Board A", projectId = "P")
        setContent(circuit, listOf(circuit))
        save()
        assertEquals("Board A", entity().circuitName)
    }

    @Test
    fun `validation - zero length rejected and non-numeric length falls back to 30`() {
        setContent()
        setFieldText("input_circuit_name", "X")
        expandSection("3. Cable Preferences")
        setFieldText("input_length", "0")
        save()
        composeTestRule.onNodeWithText("Cable length must be greater than zero.").assertExists()
        setFieldText("input_length", "abc")
        save()
        assertEquals(30.0, entity().length_m, 0.0)
    }

    @Test
    fun `save-time coercions characterize`() {
        setContent()
        setFieldText("input_circuit_name", "X")
        expandSection("3. Cable Preferences")
        setFieldText("input_load_kva", "-3")
        setFieldText("input_pf", "2")
        setFieldText("input_demand_factor", "-5")
        setFieldText("input_safety_factor", "0.5")
        setFieldText("input_voltage", "230")
        setFieldText("input_parallel_runs", "2")
        setFieldText("input_max_vd", "5")
        save()
        val e = entity()
        assertEquals(0.0, e.loadKVA, 0.0)        // max(0.0, -3)
        assertEquals(1.0, e.pf, 1e-12)           // coerceIn(0.1, 1.0)
        assertEquals(0.0, e.demandFactor, 0.0)   // coerceIn(0.0, 1.0)
        assertEquals(1.0, e.safetyFactor, 0.0)   // max(1.0, 0.5)
        assertEquals(230, e.voltageV)
        assertEquals(2, e.parallelRuns)
        assertEquals(0.05, e.maxVDpct, 1e-12)    // 5 / 100
    }

    @Test
    fun `max VD - percent to decimal round trip on edit`() {
        val circuit = CircuitEntity(id = 3, circuitName = "E1", projectId = "P", maxVDpct = 0.03)
        setContent(circuit)
        save()
        assertEquals(0.03, entity().maxVDpct, 1e-12)
    }

    // ---------- Supply category flags + assignment mapping ----------

    @Test
    fun `supply flags - NORMAL keeps flags false and nulls None assignments`() {
        val circuit = CircuitEntity(
            id = 1, circuitName = "N1", projectId = "P",
            assignedGeneratorId = "None", assignedAtsId = "None", assignedUpsId = "None"
        )
        setContent(circuit)
        save()
        val e = entity()
        assertEquals("NORMAL", e.loadType)
        assertFalse(e.generatorIncluded)
        assertFalse(e.atsIncluded)
        assertFalse(e.upsBacked)
        assertNull(e.assignedGeneratorId)
        assertNull(e.assignedAtsId)
        assertNull(e.assignedUpsId)
    }

    @Test
    fun `supply flags - EMERGENCY enables generator and ATS and keeps assignments`() {
        val circuit = CircuitEntity(
            id = 1, circuitName = "E1", projectId = "P",
            generatorIncluded = true,
            assignedGeneratorId = "GEN-01", assignedAtsId = "ATS-01"
        )
        setContent(circuit)
        save()
        val e = entity()
        assertEquals("EMERGENCY", e.loadType)
        assertTrue(e.generatorIncluded)
        assertTrue(e.atsIncluded)
        assertFalse(e.upsBacked)
        assertEquals("GEN-01", e.assignedGeneratorId)
        assertEquals("ATS-01", e.assignedAtsId)
        assertNull(e.assignedUpsId)
    }

    @Test
    fun `supply flags - UPS keeps ups assignment and stale gen and ats assignments (legacy quirk)`() {
        val circuit = CircuitEntity(
            id = 1, circuitName = "U1", projectId = "P",
            upsBacked = true, assignedUpsId = "UPS-01",
            assignedGeneratorId = "GEN-01", assignedAtsId = "ATS-01"
        )
        setContent(circuit)
        save()
        val e = entity()
        assertEquals("UPS", e.loadType)
        assertFalse(e.generatorIncluded)
        assertFalse(e.atsIncluded)
        assertTrue(e.upsBacked)
        // Quirk: assignments are persisted unconditionally (lines 1160-1163),
        // even for categories whose conditional field is not shown.
        assertEquals("GEN-01", e.assignedGeneratorId)
        assertEquals("ATS-01", e.assignedAtsId)
        assertEquals("UPS-01", e.assignedUpsId)
    }

    @Test
    fun `supply flags - EMERGENCY + UPS enables all three`() {
        val circuit = CircuitEntity(
            id = 1, circuitName = "EU1", projectId = "P",
            generatorIncluded = true, upsBacked = true,
            assignedGeneratorId = "GEN-01", assignedAtsId = "ATS-01", assignedUpsId = "UPS-01"
        )
        setContent(circuit)
        save()
        val e = entity()
        assertEquals("EMERGENCY + UPS", e.loadType)
        assertTrue(e.generatorIncluded)
        assertTrue(e.atsIncluded)
        assertTrue(e.upsBacked)
        assertEquals("GEN-01", e.assignedGeneratorId)
        assertEquals("ATS-01", e.assignedAtsId)
        assertEquals("UPS-01", e.assignedUpsId)
    }

    // ---------- Motor nature ----------

    @Test
    fun `motor nature - Motor keeps DOL default and null multiplier when untouched`() {
        setContent()
        setFieldText("input_circuit_name", "M1")
        selectDropdown("Load Nature", "Motor")
        save()
        val e = entity()
        assertEquals("Motor", e.loadNature)
        assertEquals("DOL", e.startingMethod)
        assertNull(e.startingCurrentMultiplier)
    }

    @Test
    fun `motor nature - selecting Static nulls motor fields on edit`() {
        val circuit = CircuitEntity(
            id = 2, circuitName = "M2", projectId = "P",
            loadNature = "Motor", startingMethod = "VFD", startingCurrentMultiplier = 7.0
        )
        setContent(circuit)
        selectDropdown("Load Nature", "Static")
        save()
        val e = entity()
        assertEquals("Static", e.loadNature)
        assertNull(e.startingMethod)
        assertNull(e.startingCurrentMultiplier)
    }

    @Test
    fun `motor nature - starting current multiplier is typed below its label and persisted`() {
        setContent()
        setFieldText("input_circuit_name", "M3")
        selectDropdown("Load Nature", "Motor")
        setFieldTextBelowLabel("Start Current Multiplier", "6.5")
        save()
        val e = entity()
        assertEquals("Motor", e.loadNature)
        assertEquals(6.5, e.startingCurrentMultiplier!!, 0.0)
    }

    // ---------- Feeder type / busway ----------

    @Test
    fun `feeder type - Busway persists mfg and model and switching to Cable nulls them`() {
        val circuit = CircuitEntity(
            id = 4, circuitName = "B1", projectId = "P",
            feederType = "Busway", buswayMfg = "ABC Mfg", buswayModel = "Model Z"
        )
        setContent(circuit)
        save()
        var e = entity()
        assertEquals("Busway", e.feederType)
        assertEquals("ABC Mfg", e.buswayMfg)
        assertEquals("Model Z", e.buswayModel)
        // Switch feeder type via the real dropdown, then save again.
        setContent(circuit)
        selectDropdown("Feeder Type", "Cable")
        save()
        e = entity()
        assertEquals("Cable", e.feederType)
        assertNull(e.buswayMfg)
        assertNull(e.buswayModel)
    }

    // ---------- Hierarchy recompute (lines 1076-1088) ----------

    private fun parentBoard() = listOf(
        CircuitEntity(id = 5, circuitName = "MDB-1", panelType = "MDB", level = 1, projectId = "P")
    )

    @Test
    fun `hierarchy - panel parent yields parentId, level+1, PANEL and preserves transformerId`() {
        val circuit = CircuitEntity(
            id = 6, circuitName = "C1", projectId = "P",
            fedFrom = "MDB-1", transformerId = "TX-02"
        )
        setContent(circuit, parentBoard())
        save()
        val e = entity()
        assertEquals(5, e.parentId)
        assertEquals(2, e.level)
        assertEquals("PANEL", e.sourceType)
        assertEquals("TX-02", e.transformerId) // fedFrom != Transformer -> untouched (line 1074)
    }

    @Test
    fun `hierarchy - empty or placeholder transformer id falls back to TX-01 literal`() {
        val blank = CircuitEntity(id = 6, circuitName = "T1", projectId = "P", transformerId = "")
        setContent(blank)
        save()
        assertEquals("TX-01", entity().transformerId)

        val placeholder = CircuitEntity(
            id = 6, circuitName = "T2", projectId = "P", transformerId = "\u2795 Add Another Transformer"
        )
        setContent(placeholder)
        save()
        assertEquals("TX-01", entity().transformerId)
    }

    @Test
    fun `hierarchy - existing transformer id preserved when fed from Transformer`() {
        val circuit = CircuitEntity(id = 6, circuitName = "T3", projectId = "P", transformerId = "TX-07")
        setContent(circuit)
        save()
        assertEquals("TX-07", entity().transformerId)
    }

    @Test
    fun `hierarchy - fedFrom keyword variants map sourceType`() {
        val cases = listOf(
            "Generator" to "GENERATOR",
            "Utility Grid" to "GRID",
            "ATS" to "ATS",
            "GHOST-9" to "TRANSFORMER" // unknown value, no parent match -> TRANSFORMER (line 1088)
        )
        cases.forEach { (fed, expected) ->
            val circuit = CircuitEntity(id = 6, circuitName = "K-$fed", projectId = "P", fedFrom = fed)
            setContent(circuit)
            save()
            val e = entity()
            assertEquals(expected, e.sourceType)
            assertNull(e.parentId)
            assertEquals(0, e.level)
        }
    }

    @Test
    fun `hierarchy - parent name matching is exact and case-sensitive (legacy quirk)`() {
        // existing board "MDB-1"; fedFrom stored as " mdb-1 " (padded, wrong case)
        val circuit = CircuitEntity(id = 6, circuitName = "C2", projectId = "P", fedFrom = " mdb-1 ")
        setContent(circuit, parentBoard())
        save()
        val e = entity()
        assertNull(e.parentId)
        assertEquals(0, e.level)
        assertEquals("TRANSFORMER", e.sourceType)
    }

    // ---------- Unmanaged field preservation (copy() vs fresh constructor) ----------

    @Test
    fun `edit mode preserves unmanaged fields and recomputes managed hierarchy`() {
        val circuit = CircuitEntity(
            id = 7, projectId = "PRJ-77", circuitName = "Feed 7", loadKVA = 12.0, maxVDpct = 0.02,
            overrideDeviceType = "MCCB", overrideDeviceSize = 63.0, overrideCableSize = "25 mm2",
            overrideNeutralSize = "16 mm2", overrideEarthSize = "10 mm2",
            assignedPhase = "L1", diversityGroup = "DG-1", active = false,
            directLoadConfirmed = true, phaseImbalanceLimitOverridePct = 12.5, scRatingKA = 9.5,
            parentId = 5, level = 1, sourceType = "PANEL", loadCategory = "PWR"
        )
        setContent(circuit)
        setFieldText("input_circuit_name", "Feed 7 renamed")
        save()
        val e = entity()
        assertEquals(7, e.id)
        assertEquals("PRJ-77", e.projectId)
        assertEquals("MCCB", e.overrideDeviceType)
        assertEquals(63.0, e.overrideDeviceSize!!, 0.0)
        assertEquals("25 mm2", e.overrideCableSize)
        assertEquals("16 mm2", e.overrideNeutralSize)
        assertEquals("10 mm2", e.overrideEarthSize)
        assertEquals("L1", e.assignedPhase)
        assertEquals("DG-1", e.diversityGroup)
        assertFalse(e.active)
        assertTrue(e.directLoadConfirmed == true)
        assertEquals(12.5, e.phaseImbalanceLimitOverridePct!!, 0.0)
        assertEquals(9.5, e.scRatingKA!!, 0.0)
        assertEquals("PWR", e.loadCategory)
        // Managed hierarchy IS recomputed from fedFrom ("Transformer" default),
        // overwriting the stored parentId/level/sourceType:
        assertNull(e.parentId)
        assertEquals(0, e.level)
        assertEquals("TRANSFORMER", e.sourceType)
        assertEquals(0.02, e.maxVDpct, 1e-12)
    }

    // ---------- Panel header save mapping ----------

    @Test
    fun `panel header - populated fields round trip and scRatingKA is preserved`() {
        val circuit = CircuitEntity(
            id = 8, circuitName = "MDB-2", projectId = "P", panelType = "MDB",
            busbarRatingA = 250.5, mainIncomingType = "MCCB", mainIncomingCount = 2,
            ipRating = "IP54", panelLocation = "Basement", cableEntry = "Top",
            feederDescription = "desc-1", numberOfWays = 12, scRatingKA = 9.5
        )
        setContent(circuit)
        save()
        val e = entity()
        assertEquals(250.5, e.busbarRatingA!!, 0.0)
        assertEquals("MCCB", e.mainIncomingType)
        assertEquals(2, e.mainIncomingCount)
        assertEquals("IP54", e.ipRating)
        assertEquals("Basement", e.panelLocation)
        assertEquals("Top", e.cableEntry)
        assertEquals("desc-1", e.feederDescription)
        assertEquals(12, e.numberOfWays)
        assertEquals(9.5, e.scRatingKA!!, 0.0) // preserved via copy() (line 1172)
        assertEquals("MDB", e.panelType)
    }

    @Test
    fun `panel header - blank inputs are saved as null`() {
        val circuit = CircuitEntity(id = 8, circuitName = "MDB-3", projectId = "P", panelType = "MDB")
        setContent(circuit)
        save()
        val e = entity()
        assertNull(e.busbarRatingA)
        assertNull(e.mainIncomingType)
        assertNull(e.mainIncomingCount)
        assertNull(e.ipRating)
        assertNull(e.panelLocation)
        assertNull(e.cableEntry)
        assertNull(e.feederDescription)
        assertNull(e.numberOfWays)
    }

    // ---------- Load category default overwrite (dropdown-driven) ----------

    @Test
    fun `load category - selecting LTG applies default pf and df and persists code`() {
        setContent()
        setFieldText("input_circuit_name", "L")
        selectDropdown("Load Category (Classification & Default Suggestion)", "LTG \u2014 Lighting")
        // applied live to the fields (lines 348-352)
        save()
        val e = entity()
        assertEquals("LTG", e.loadCategory)
        assertEquals(0.92, e.pf, 1e-12)
        assertEquals(0.90, e.demandFactor, 1e-12)
    }

    // ---------- Error lifecycle + dismissal ----------

    @Test
    fun `error message clears when name is edited`() {
        setContent()
        save()
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertExists()
        setFieldText("input_circuit_name", "Z") // onValueChange clears error (lines 219-223)
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertDoesNotExist()
    }

    @Test
    fun `cancel dismisses without saving`() {
        setContent()
        setFieldText("input_circuit_name", "Never saved")
        composeTestRule.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, dismissCount)
        assertEquals(0, saved.size)
    }
}
