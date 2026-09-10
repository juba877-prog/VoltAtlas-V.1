package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies that the active-identifier resolution for each equipment panel
 * yields null when the corresponding collection is empty — not a fallback
 * string from the legacy single-instance settings object.
 */
class PowerSystemsIdentifierFallbackTest {

    /** Minimal Success state with all required fields and empty equipment collections. */
    private fun emptyEquipmentState() = UiState.Success(
        circuits = emptyList(),
        transformers = emptyList(),
        sizingResults = emptyMap(),
        transformerResults = emptyMap(),
        boq = emptyList(),
        drums = emptyList(),
        procurement = emptyList(),
        panels = emptyList(),
        generators = emptyList(),
        atsList = emptyList(),
        upsList = emptyList(),
        pfCorrectionResults = emptyList()
    )

    @Test
    fun testGeneratorActiveIdIsNullWhenCollectionEmpty() {
        val state = emptyEquipmentState()
        val generators = state.generators  // emptyList()

        val resolution = resolveEquipmentSelection(
            currentIds = generators.map { it.generatorId },
            selectedId = ""
        )

        assertNull("Active generator ID must be null when collection is empty", resolution.activeId)
        assertEquals("Updated selected ID must be empty", "", resolution.updatedSelectedId)

        val activeGen = generators.firstOrNull { it.generatorId == resolution.activeId }
        assertNull("Active generator entity must be null when collection is empty", activeGen)
    }

    @Test
    fun testAtsActiveIdIsNullWhenCollectionEmpty() {
        val state = emptyEquipmentState()
        val atsList = state.atsList  // emptyList()

        val resolution = resolveEquipmentSelection(
            currentIds = atsList.map { it.atsId },
            selectedId = ""
        )

        assertNull("Active ATS ID must be null when collection is empty", resolution.activeId)
        assertEquals("Updated selected ATS ID must be empty", "", resolution.updatedSelectedId)

        val activeAts = atsList.firstOrNull { it.atsId == resolution.activeId }
        assertNull("Active ATS entity must be null when collection is empty", activeAts)
    }

    @Test
    fun testUpsActiveIdIsNullWhenCollectionEmpty() {
        val state = emptyEquipmentState()
        val upsList = state.upsList  // emptyList()

        val resolution = resolveEquipmentSelection(
            currentIds = upsList.map { it.upsId },
            selectedId = ""
        )

        assertNull("Active UPS ID must be null when collection is empty", resolution.activeId)
        assertEquals("Updated selected UPS ID must be empty", "", resolution.updatedSelectedId)

        val activeUps = upsList.firstOrNull { it.upsId == resolution.activeId }
        assertNull("Active UPS entity must be null when collection is empty", activeUps)
    }
}
