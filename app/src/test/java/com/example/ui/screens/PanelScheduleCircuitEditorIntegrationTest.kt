package com.example.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class PanelScheduleCircuitEditorIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    private fun createCircuit(
        id: Int,
        name: String = "Panel-$id",
        loadKVA: Double = 50.0,
        fedFrom: String = "Transformer",
        parentId: Int? = null,
        panelType: String? = "DB",
        manufacturer: String = "Ducab",
        cableType: String = "Cu/XLPE/PVC",
        lengthM: Double = 30.0,
        overrideCableSize: String? = null
    ) = CircuitEntity(
        id = id,
        projectId = "DEFAULT_PROJECT",
        circuitName = name,
        loadKVA = loadKVA,
        fedFrom = fedFrom,
        parentId = parentId,
        panelType = panelType,
        manufacturer = manufacturer,
        cableType = cableType,
        length_m = lengthM,
        overrideCableSize = overrideCableSize
    )

    private fun createSuccessState(circuits: List<CircuitEntity>) = UiState.Success(
        circuits = circuits,
        transformers = emptyList(),
        sizingResults = emptyMap(),
        shortCircuitResults = emptyMap(),
        transformerResults = emptyMap(),
        boq = emptyList(),
        drums = emptyList(),
        procurement = emptyList(),
        panels = emptyList(),
        pfCorrectionResults = emptyList()
    )

    private fun setScreen(circuits: List<CircuitEntity>) {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                PanelScheduleScreen(
                    viewModel = viewModel,
                    uiState = createSuccessState(circuits)
                )
            }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = EngineeringViewModel(context)
    }

    private fun nameInputField() =
        composeTestRule.onNode(hasSetTextAction() and hasParent(hasTestTag("input_circuit_name")))

    private fun getEditableText(): String {
        val node = nameInputField().fetchSemanticsNode()
        return node.config[SemanticsProperties.EditableText].text
    }

    // =========================================================================
    // 1. SCREEN COMPOSABLE TESTS: NEW PANEL STUB & DISMISSAL CLEANUP
    // =========================================================================

    @After
    fun tearDown() {
        // KT-12: this class reaches AppDatabase.getDatabase() (directly or via
        // EngineeringViewModel), which caches the instance process-wide together with an
        // absolute DB file path inside the current Robolectric sandbox. Robolectric rotates that
        // sandbox per @Config while Gradle reuses one JVM, so a surviving instance points at a
        // path that no longer exists and later suites fail with SQLITE_CANTOPEN (code 14).
        AppDatabase.resetInstanceForTesting()
    }

    @Test
    fun test01_openNewPanelAdd_triggersZeroIdStubWithSevenStepWorkflow() {
        val p1 = createCircuit(id = 1, name = "MDB-Main", panelType = "MDB")
        setScreen(listOf(p1))

        // Trigger real Create New Panel action in TopBar
        composeTestRule.onNodeWithTag("create_new_panel_btn").assertIsDisplayed().performClick()

        // Mobile editor appears in dialog mode
        composeTestRule.onNodeWithTag("circuit_editor_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit Circuit").assertIsDisplayed()

        // Panel Type is DB by default in PanelScheduleScreen stub, so 7 steps apply (including Panel Header)
        // Step indicator "7" must be present in the progress rail
        composeTestRule.onNodeWithTag("progress_step_node_7").assertExists()
    }

    @Test
    fun test02_dismissCleanup_closesDialogAndClearsState() {
        val p1 = createCircuit(id = 1, name = "MDB-Main", panelType = "MDB")
        setScreen(listOf(p1))

        // Open Add
        composeTestRule.onNodeWithTag("create_new_panel_btn").performClick()
        composeTestRule.onNodeWithTag("circuit_editor_header").assertIsDisplayed()

        // Click close
        composeTestRule.onNodeWithTag("circuit_editor_close_btn").performClick()

        // Dialog closes
        composeTestRule.onNodeWithTag("circuit_editor_header").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_new_panel_btn").assertIsDisplayed()
    }

    @Test
    fun test03_r2SessionIsolation_reopeningPanelAddResetsDraft() {
        val p1 = createCircuit(id = 1, name = "MDB-Main", panelType = "MDB")
        setScreen(listOf(p1))

        // 1. Open Add and type text
        composeTestRule.onNodeWithTag("create_new_panel_btn").performClick()
        nameInputField().performTextInput("Temp Panel Name")
        assertEquals("Temp Panel Name", getEditableText())

        // 2. Dismiss
        composeTestRule.onNodeWithTag("circuit_editor_close_btn").performClick()
        composeTestRule.onNodeWithTag("circuit_editor_header").assertDoesNotExist()

        // 3. Re-open Add
        composeTestRule.onNodeWithTag("create_new_panel_btn").performClick()

        // Session reset: draft is clean empty string
        assertEquals("", getEditableText())
    }

    // =========================================================================
    // 2. PANEL HEADER APPLICABILITY CONTRACT
    // =========================================================================

    @Test
    fun test04_panelHeaderApplicability_dbRequiresPanelHeader_loadExcludesPanelHeader() {
        // Panel types require Panel Header step
        val panelTypes = listOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
        for (pType in panelTypes) {
            val steps = CircuitWorkflow.getApplicableSteps(pType)
            assertEquals("Panel type $pType must have 7 steps", 7, steps.size)
            assertTrue("Panel type $pType must include PANEL_HEADER", steps.contains(CircuitWorkflowStep.PANEL_HEADER))
        }

        // LOAD type excludes Panel Header step
        val loadSteps = CircuitWorkflow.getApplicableSteps("LOAD")
        assertEquals("LOAD type must have 6 steps", 6, loadSteps.size)
        assertFalse("LOAD type must NOT include PANEL_HEADER", loadSteps.contains(CircuitWorkflowStep.PANEL_HEADER))
    }

    // =========================================================================
    // 3. REAL CALLER COORDINATOR TESTS: ROUTING, KEYS, & NO OVERRIDE LEAKAGE
    // =========================================================================

    @Test
    fun test05_saveNewPanel_callsAddExactlyOnceAndUpdatesSelectedOptionKey() {
        var addCalls = 0
        var updateCalls = 0
        var dismissCalls = 0
        var updatedKey: String? = null
        var addedEntity: CircuitEntity? = null

        val newPanel = createCircuit(id = 0, name = "Sub-DB-Floor2", panelType = "DB")

        PanelScheduleSaveCoordinator.handleSave(
            savedCircuit = newPanel,
            isNewCircuit = true,
            onAdd = {
                addCalls++
                addedEntity = it
            },
            onUpdate = { updateCalls++ },
            onUpdateSelectedOptionKey = { updatedKey = it },
            onDismiss = { dismissCalls++ }
        )

        assertEquals("onAdd must be called exactly once", 1, addCalls)
        assertEquals("onUpdate must not be called", 0, updateCalls)
        assertEquals("onDismiss must be called exactly once", 1, dismissCalls)
        assertNotNull(addedEntity)
        // With id == 0, key computation falls back to name_
        assertEquals("name_Sub-DB-Floor2", updatedKey)
    }

    @Test
    fun test06_saveExistingPanel_callsUpdateExactlyOnceAndDoesNotChangeSelectedKey() {
        var addCalls = 0
        var updateCalls = 0
        var dismissCalls = 0
        var keyUpdated = false
        var updatedEntity: CircuitEntity? = null

        val existingPanel = createCircuit(id = 15, name = "MDB-East", panelType = "MDB")

        PanelScheduleSaveCoordinator.handleSave(
            savedCircuit = existingPanel,
            isNewCircuit = false,
            onAdd = { addCalls++ },
            onUpdate = {
                updateCalls++
                updatedEntity = it
            },
            onUpdateSelectedOptionKey = { keyUpdated = true },
            onDismiss = { dismissCalls++ }
        )

        assertEquals("onUpdate must be called exactly once", 1, updateCalls)
        assertEquals("onAdd must not be called", 0, addCalls)
        assertEquals("onDismiss must be called exactly once", 1, dismissCalls)
        assertFalse("selectedOptionKey should not be overwritten on existing edit", keyUpdated)
        assertEquals(15, updatedEntity?.id)
    }

    @Test
    fun test07_selectedOptionKey_computesIdKeyWhenIdNonZero() {
        val panelWithAssignedId = createCircuit(id = 105, name = "DB-Ground", panelType = "DB")
        val key = PanelScheduleSaveCoordinator.computeSelectedOptionKey(panelWithAssignedId)
        assertEquals("id_105", key)
    }

    @Test
    fun test08_selectedOptionKey_computesNameKeyWhenIdZero() {
        val panelWithZeroId = createCircuit(id = 0, name = "DB-Ground", panelType = "DB")
        val key = PanelScheduleSaveCoordinator.computeSelectedOptionKey(panelWithZeroId)
        assertEquals("name_DB-Ground", key)
    }

    @Test
    fun test09_selectedOptionKey_returnsNullForNonPanelType() {
        val nonPanel = createCircuit(id = 0, name = "Motor-Feeder-1", panelType = "LOAD")
        val key = PanelScheduleSaveCoordinator.computeSelectedOptionKey(nonPanel)
        assertNull("LOAD type must not produce a panel selection key", key)
    }

    @Test
    fun test10_noGridOnlyOverrideInvalidationLeakage() {
        // In PanelScheduleScreen, overrideCableSize is NOT cleared when manufacturer/cable changes
        var persistedEntity: CircuitEntity? = null
        val originalPanel = createCircuit(
            id = 5,
            name = "Panel-5",
            panelType = "SMDB",
            manufacturer = "Ducab",
            cableType = "Cu/XLPE/PVC",
            overrideCableSize = "150"
        )
        // User changed manufacturer and cableType in editor
        val savedPanel = originalPanel.copy(
            manufacturer = "Elsewedy",
            cableType = "Al/XLPE/PVC"
        )

        PanelScheduleSaveCoordinator.handleSave(
            savedCircuit = savedPanel,
            isNewCircuit = false,
            onAdd = {},
            onUpdate = { persistedEntity = it },
            onUpdateSelectedOptionKey = {},
            onDismiss = {}
        )

        assertNotNull(persistedEntity)
        assertEquals("PanelScheduleScreen must preserve overrideCableSize without Grid-only invalidation leakage",
            "150", persistedEntity?.overrideCableSize)
    }
}
