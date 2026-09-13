package com.example.ui.dialogs.circuiteditor.state

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.validation.CircuitEditorValidator
import com.example.ui.dialogs.circuiteditor.validation.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 pure tests for [CircuitEditorSessionIdentity] and the deterministic
 * [CircuitEditorSaveableState.Saver]. No Compose, no Robolectric — pure JVM.
 *
 * Evidence Label: NEW_MAPPER_SPECIFIED
 */
class CircuitEditorSaveableStateTest {

    // ---------- Session identity matrix ----------

    @Test
    fun test01_editSessionIdentityUsesIdNameProject() {
        val a = CircuitEditorSessionIdentity.forCircuit(
            CircuitEntity(id = 12, circuitName = "DB-A", projectId = "P1")
        )
        assertEquals(CircuitEditorSessionOrigin.EDIT, a.origin)
        assertEquals(12, a.circuitId)
        assertEquals("DB-A", a.circuitName)
        assertEquals("P1", a.projectId)
    }

    @Test
    fun test02_gridAddIsNullOrigin() {
        val id = CircuitEditorSessionIdentity.forCircuit(null)
        assertEquals(CircuitEditorSessionOrigin.ADD_NULL, id.origin)
        assertEquals(0, id.circuitId)
    }

    @Test
    fun test03_panelScheduleStubAddIsZeroIdOrigin() {
        val id = CircuitEditorSessionIdentity.forCircuit(
            CircuitEntity(id = 0, circuitName = "New Panel", projectId = "P1")
        )
        assertEquals(CircuitEditorSessionOrigin.ADD_STUB_ZERO, id.origin)
        assertEquals(0, id.circuitId)
        assertEquals("New Panel", id.circuitName)
    }

    @Test
    fun test04_editAAndEditBDiffer() {
        val a = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 12, circuitName = "DB-A"))
        val b = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 13, circuitName = "DB-B"))
        assertNotEquals(a, b)
    }

    @Test
    fun test05_gridAddAndStubAddNeverCollide() {
        val gridAdd = CircuitEditorSessionIdentity.forCircuit(null)
        val stubAdd = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 0, circuitName = ""))
        assertNotEquals(gridAdd, stubAdd)
    }

    @Test
    fun test06_editedCircuitNameChangeChangesIdentity() {
        val before = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 12, circuitName = "DB-A"))
        val after = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 12, circuitName = "DB-A2"))
        assertNotEquals(before, after)
    }

    @Test
    fun test07_snapshotInputsAreNotPartOfIdentity() {
        // Equipment/catalog/preference emissions must not reset a session.
        val before = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 12, circuitName = "DB-A"))
        val after = CircuitEditorSessionIdentity.forCircuit(CircuitEntity(id = 12, circuitName = "DB-A"))
        assertEquals(before, after)
    }

    // ---------- Saver round-trip fixtures ----------

    private val draftWithAllKinds = CircuitEditorDraft(
        name = "DB-Alpha",
        panelType = "SMDB",
        fedFrom = "Transformer",
        transformerId = "TX-RET-77",
        loadCategory = "PWR",
        loadNature = "Motor",
        startingMethod = "VFD",
        startingCurrentMultiplier = "6.5",
        loadKVA = "123.4",
        pf = "0.92",
        demandFactor = "0.9",
        voltageV = "690",
        safetyFactor = "1.25",
        phases = "3P",
        isHarmonic = true,
        supplyCategory = "EMERGENCY + UPS",
        assignedAtsId = "ATS-2",
        assignedGeneratorId = "GEN-3",
        assignedUpsId = "UPS-4",
        feederType = "Busway",
        manufacturer = "Elsewedy",
        cableType = "Cu/XLPE/SWA/PVC - 4C",
        parallelRuns = "2",
        length_m = "77.5",
        maxVDpct = "4.25",
        buswayMfg = "Elsewedy",
        buswayModel = "Power Link",
        filterMat = "Copper",
        filterArmour = "Armoured",
        filterCores = "4C",
        filterQuery = "swa",
        installation = "Laid Direct in Ground",
        airTempC = "45",
        gndTempC = "35",
        burialDepth_m = "0.8",
        soilRes = "150",
        noCircuits = "3",
        formation = "Trefoil",
        spacing = "Touching",
        busbarRatingA = "2500",
        mainIncomingType = "ACB",
        mainIncomingCount = "2",
        ipRating = "IP54",
        panelLocation = "MSB Room",
        cableEntry = "Bottom",
        numberOfWays = "36",
        feederDescription = "Riser to level 3"
    )

    private fun state(
        draft: CircuitEditorDraft = draftWithAllKinds,
        step: CircuitWorkflowStep = CircuitWorkflowStep.FEEDER,
        visited: List<CircuitWorkflowStep> = listOf(
            CircuitWorkflowStep.IDENTITY,
            CircuitWorkflowStep.LOAD,
            CircuitWorkflowStep.SUPPLY,
            CircuitWorkflowStep.FEEDER
        ),
        identityError: String? = "A circuit/panel named 'DB-Alpha' already exists — choose a different name.",
        reviewError: String? = null
    ) = CircuitEditorSaveableState(
        draft = draft,
        currentStepName = step.name,
        visitedStepNames = visited.map { it.name },
        identityError = identityError,
        reviewError = reviewError
    )

    private val saverScope = object : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(any: Any): Boolean = true
    }

    private fun roundTrip(original: CircuitEditorSaveableState): CircuitEditorSaveableState {
        val saver = CircuitEditorSaveableState.Saver
        val payload = with(saverScope) { with(saver) { save(original) } }
        return saver.restore(payload!!)!!
    }

    @Test
    fun test08_saverRoundTripPreservesEveryDraftField() {
        val original = state()
        val restored = roundTrip(original)
        assertEquals(original, restored)
    }

    @Test
    fun test09_saverPreservesNullNullableFields() {
        val original = state(
            draft = CircuitEditorDraft(name = "Nulls", loadCategory = null),
            identityError = null
        )
        val restored = roundTrip(original)
        assertNull(restored.draft.loadCategory)
        assertNull(restored.identityError)
        assertEquals("Nulls", restored.draft.name)
    }

    @Test
    fun test10_saverPayloadContainsOnlyPrimitivesStringsAndBooleans() {
        val saver = CircuitEditorSaveableState.Saver
        val payload = (with(saverScope) { with(saver) { save(state()) } }!!) as List<Any>
        payload.forEach { entry ->
            assertTrue(
                "saved payload must contain only String/Boolean/Int primitives, was ${entry::class.java}",
                entry is String || entry is Boolean || entry is Int
            )
        }
    }

    @Test
    fun test11_restoredStepNamesMapBackToWorkflowSteps() {
        val restored = state(visited = listOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD))
        assertEquals(CircuitWorkflowStep.FEEDER, restored.currentStepName.toCircuitWorkflowStepOrDefault())
        assertEquals(
            setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            restored.visitedStepNames.toCircuitWorkflowStepSet()
        )
    }

    @Test
    fun test12_unknownRestoredStepNamesFallBackDefensively() {
        assertEquals(
            CircuitWorkflowStep.IDENTITY,
            "NOT_A_REAL_STEP".toCircuitWorkflowStepOrDefault()
        )
        assertEquals(
            emptySet<CircuitWorkflowStep>(),
            listOf("BOGUS_STEP").toCircuitWorkflowStepSet()
        )
    }

    @Test
    fun test13_initialStateStartsAtIdentityWithOnlyIdentityVisited() {
        val initial = CircuitEditorSaveableState.initial(CircuitEditorDraft(name = "X"))
        assertEquals(CircuitWorkflowStep.IDENTITY.name, initial.currentStepName)
        assertEquals(listOf(CircuitWorkflowStep.IDENTITY.name), initial.visitedStepNames)
        assertNull(initial.identityError)
        assertNull(initial.reviewError)
    }

    @Test
    fun test14_restoredDraftIsValidationCompatible() {
        // The restored draft must behave identically to a live draft in validation;
        // derived values are recomputed, never saved.
        val original = state(draft = draftWithAllKinds.copy(length_m = "0"))
        val restored = roundTrip(original)
        val liveValidation = CircuitEditorValidator.validateForSave(original.draft, emptyList(), 0)
        val restoredValidation = CircuitEditorValidator.validateForSave(restored.draft, emptyList(), 0)
        assertEquals(liveValidation, restoredValidation)
        assertTrue(restoredValidation is ValidationResult.Error)
    }

    @Test
    fun test15_restoredDraftFeedsSourceResolution() {
        val original = state(draft = draftWithAllKinds)
        val restored = roundTrip(original)
        val resolution = com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
            .resolveSourceHierarchy(
                fedFrom = restored.draft.fedFrom,
                draftTransformerId = restored.draft.transformerId,
                existingCircuits = emptyList(),
                availableTransformers = emptyList()
            )
        assertEquals(restored.draft.transformerId, resolution.finalTxId)
    }

    @Test
    fun test16_fabricationProhibitionHoldsInSaver() {
        // The Saver neither adds nor transforms any identifier; a draft with an absent
        // transformer stays absent through save/restore.
        val original = state(draft = CircuitEditorDraft(name = "NoTx", transformerId = ""))
        val restored = roundTrip(original)
        assertEquals("", restored.draft.transformerId)
        assertFalse(restored.draft.transformerId.contains("TX-01"))
        assertNull(restored.draft.loadCategory)
    }
}