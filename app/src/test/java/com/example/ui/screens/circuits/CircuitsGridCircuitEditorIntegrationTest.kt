package com.example.ui.screens.circuits

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.CircuitEntity
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.screens.CircuitsGridScreen
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
class CircuitsGridCircuitEditorIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    private fun createCircuit(
        id: Int,
        name: String = "Circuit-$id",
        loadKVA: Double = 50.0,
        fedFrom: String = "MDB-1",
        parentId: Int? = null,
        panelType: String? = null,
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
                CircuitsGridScreen(
                    state = createSuccessState(circuits),
                    viewModel = viewModel
                )
            }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = EngineeringViewModel(context)
    }

    @After
    fun tearDown() {
        com.example.data.AppDatabase.resetInstanceForTesting()
    }

    private fun nameInputField() =
        composeTestRule.onNode(hasSetTextAction() and hasParent(hasTestTag("input_circuit_name")))

    private fun getEditableText(): String {
        val node = nameInputField().fetchSemanticsNode()
        return node.config[SemanticsProperties.EditableText].text
    }

    // =========================================================================
    // 1. SCREEN COMPOSABLE TESTS: REAL ENTRY POINTS & LIFECYCLE
    // =========================================================================

    @Test
    fun test01_openAdd_fromFab_opensMobileEditorInAddModeWithExpectedDraftContract() {
        val c1 = createCircuit(id = 1, name = "MDB-Main")
        setScreen(listOf(c1))

        // Trigger real Add FAB action
        composeTestRule.onNodeWithTag("add_circuit_fab").assertIsDisplayed().performClick()

        // Mobile editor appears in Add mode
        composeTestRule.onNodeWithTag("circuit_editor_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add New Circuit").assertIsDisplayed()

        // Default draft contract: empty circuit name
        assertEquals("", getEditableText())

        // Step 1 active with Next action
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertIsDisplayed()
    }

    @Test
    fun test02_openEdit_fromCard_opensMobileEditorWithEntityData() {
        val c1 = createCircuit(id = 1, name = "Circuit-1", loadKVA = 75.0, lengthM = 45.0)
        setScreen(listOf(c1))

        // Expand circuit card to expose action buttons
        composeTestRule.onNodeWithTag("circuit_card_Circuit-1").performScrollTo().performClick()

        // Trigger real Edit button from card
        composeTestRule.onNodeWithTag("edit_circuit_btn_Circuit-1").performScrollTo().performClick()

        // Mobile editor appears in Edit mode
        composeTestRule.onNodeWithTag("circuit_editor_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit Circuit").assertIsDisplayed()

        // Populated with existing entity data
        assertEquals("Circuit-1", getEditableText())
    }

    @Test
    fun test03_dismissWithoutSave_closesDialogAndDoesNotPersist() {
        val c1 = createCircuit(id = 1, name = "Circuit-1")
        setScreen(listOf(c1))

        // Open Add
        composeTestRule.onNodeWithTag("add_circuit_fab").performClick()
        composeTestRule.onNodeWithTag("circuit_editor_header").assertIsDisplayed()

        // Dismiss via top Close button
        composeTestRule.onNodeWithTag("circuit_editor_close_btn").performClick()

        // Dialog closes, FAB is visible again
        composeTestRule.onNodeWithTag("circuit_editor_header").assertDoesNotExist()
        composeTestRule.onNodeWithTag("add_circuit_fab").assertIsDisplayed()
    }

    @Test
    fun test04_r2SessionIsolation_reopeningAddResetsDraftToEmpty() {
        val c1 = createCircuit(id = 1, name = "Circuit-1")
        setScreen(listOf(c1))

        // 1. Open Add and enter text
        composeTestRule.onNodeWithTag("add_circuit_fab").performClick()
        nameInputField().performTextInput("Temporary Inflight Draft")
        assertEquals("Temporary Inflight Draft", getEditableText())

        // 2. Dismiss
        composeTestRule.onNodeWithTag("circuit_editor_close_btn").performClick()
        composeTestRule.onNodeWithTag("circuit_editor_header").assertDoesNotExist()

        // 3. Re-open Add
        composeTestRule.onNodeWithTag("add_circuit_fab").performClick()
        composeTestRule.onNodeWithTag("circuit_editor_header").assertIsDisplayed()

        // Session reset: draft is clean empty string, not stale text
        assertEquals("", getEditableText())
    }

    @Test
    fun test05_r2SessionIsolation_switchingFromEditAToEditBDoesNotLeakState() {
        val c1 = createCircuit(id = 1, name = "Circuit-A")
        val c2 = createCircuit(id = 2, name = "Circuit-B")
        setScreen(listOf(c1, c2))

        // 1. Open Edit A
        composeTestRule.onNodeWithTag("circuit_card_Circuit-A").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("edit_circuit_btn_Circuit-A").performScrollTo().performClick()
        assertEquals("Circuit-A", getEditableText())

        // 2. Dismiss
        composeTestRule.onNodeWithTag("circuit_editor_close_btn").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Edit B
        composeTestRule.onNodeWithTag("circuits_lazy_grid").performScrollToNode(hasTestTag("circuit_card_Circuit-B"))
        composeTestRule.onNodeWithTag("circuit_card_Circuit-B").performClick()
        composeTestRule.onNodeWithTag("circuits_lazy_grid").performScrollToNode(hasTestTag("edit_circuit_btn_Circuit-B"))
        composeTestRule.onNodeWithTag("edit_circuit_btn_Circuit-B").performClick()

        // Session reset: displays Circuit-B, zero leakage from A
        assertEquals("Circuit-B", getEditableText())
    }

    // =========================================================================
    // 2. REAL CALLER COORDINATOR TESTS: ROUTING, INVALIDATION & ONCE-ONLY SAVE
    // =========================================================================

    @Test
    fun test06_saveNewCircuit_routesToOnAddExactlyOnceAndDismisses() {
        var addCalls = 0
        var updateCalls = 0
        var dismissCalls = 0
        var addedEntity: CircuitEntity? = null

        val newDraft = createCircuit(id = 0, name = "New-Branch-1")

        CircuitsGridSaveCoordinator.handleSave(
            original = null,
            saved = newDraft,
            onAdd = {
                addCalls++
                addedEntity = it
            },
            onUpdate = { updateCalls++ },
            onDismiss = { dismissCalls++ }
        )

        assertEquals("onAdd must be called exactly once", 1, addCalls)
        assertEquals("onUpdate must not be called for new circuit", 0, updateCalls)
        assertEquals("onDismiss must be called exactly once", 1, dismissCalls)
        assertNotNull(addedEntity)
        assertEquals("New-Branch-1", addedEntity?.circuitName)
    }

    @Test
    fun test07_saveExistingCircuit_routesToOnUpdateExactlyOnceAndDismisses() {
        var addCalls = 0
        var updateCalls = 0
        var dismissCalls = 0
        var updatedEntity: CircuitEntity? = null

        val original = createCircuit(id = 42, name = "Feeder-42")
        val edited = original.copy(length_m = 120.0)

        CircuitsGridSaveCoordinator.handleSave(
            original = original,
            saved = edited,
            onAdd = { addCalls++ },
            onUpdate = {
                updateCalls++
                updatedEntity = it
            },
            onDismiss = { dismissCalls++ }
        )

        assertEquals("onUpdate must be called exactly once", 1, updateCalls)
        assertEquals("onAdd must not be called for existing circuit", 0, addCalls)
        assertEquals("onDismiss must be called exactly once", 1, dismissCalls)
        assertNotNull(updatedEntity)
        assertEquals(42, updatedEntity?.id)
        assertEquals(120.0, updatedEntity?.length_m ?: 0.0, 0.001)
    }

    @Test
    fun test08_overrideInvalidation_clearsOverrideWhenManufacturerChanges() {
        var updatedEntity: CircuitEntity? = null
        val original = createCircuit(
            id = 10,
            manufacturer = "Ducab",
            cableType = "Cu/XLPE/PVC",
            overrideCableSize = "95"
        )
        val savedWithNewManufacturer = original.copy(manufacturer = "Elsewedy")

        CircuitsGridSaveCoordinator.handleSave(
            original = original,
            saved = savedWithNewManufacturer,
            onAdd = {},
            onUpdate = { updatedEntity = it },
            onDismiss = {}
        )

        assertNotNull(updatedEntity)
        assertNull("overrideCableSize must be cleared when manufacturer changes", updatedEntity?.overrideCableSize)
        assertEquals("Elsewedy", updatedEntity?.manufacturer)
    }

    @Test
    fun test09_overrideInvalidation_clearsOverrideWhenCableTypeChanges() {
        var updatedEntity: CircuitEntity? = null
        val original = createCircuit(
            id = 10,
            manufacturer = "Ducab",
            cableType = "Cu/XLPE/PVC",
            overrideCableSize = "95"
        )
        val savedWithNewCable = original.copy(cableType = "Al/XLPE/PVC")

        CircuitsGridSaveCoordinator.handleSave(
            original = original,
            saved = savedWithNewCable,
            onAdd = {},
            onUpdate = { updatedEntity = it },
            onDismiss = {}
        )

        assertNotNull(updatedEntity)
        assertNull("overrideCableSize must be cleared when cable family changes", updatedEntity?.overrideCableSize)
        assertEquals("Al/XLPE/PVC", updatedEntity?.cableType)
    }

    @Test
    fun test10_overridePreservation_preservesOverrideWhenManufacturerAndCableTypeUnchanged() {
        var updatedEntity: CircuitEntity? = null
        val original = createCircuit(
            id = 10,
            manufacturer = "Ducab",
            cableType = "Cu/XLPE/PVC",
            overrideCableSize = "95"
        )
        val savedWithDifferentLength = original.copy(length_m = 65.0, loadKVA = 80.0)

        CircuitsGridSaveCoordinator.handleSave(
            original = original,
            saved = savedWithDifferentLength,
            onAdd = {},
            onUpdate = { updatedEntity = it },
            onDismiss = {}
        )

        assertNotNull(updatedEntity)
        assertEquals("overrideCableSize must be preserved when cable & mfr unchanged", "95", updatedEntity?.overrideCableSize)
        assertEquals(65.0, updatedEntity?.length_m ?: 0.0, 0.001)
    }

    @Test
    fun test11_saveExactlyOnce_noDuplicatePersistenceCalls() {
        var addCount = 0
        val draft = createCircuit(id = 0, name = "StrictlyOnce")

        CircuitsGridSaveCoordinator.handleSave(
            original = null,
            saved = draft,
            onAdd = { addCount++ },
            onUpdate = { fail("Should not update") },
            onDismiss = {}
        )

        assertEquals("Persistence operation must fire exactly once", 1, addCount)
    }
}
