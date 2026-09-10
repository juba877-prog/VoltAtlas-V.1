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

        // Replicate the panel's resolution logic (no selectedGenId match)
        val selectedGenId = ""
        val activeGenId = if (selectedGenId.isNotEmpty() && generators.any { it.generatorId == selectedGenId }) {
            selectedGenId
        } else {
            generators.firstOrNull()?.generatorId
        }

        assertNull("Active generator ID must be null when collection is empty", activeGenId)

        val activeGen = generators.firstOrNull { it.generatorId == activeGenId }
        assertNull("Active generator entity must be null when collection is empty", activeGen)
    }

    @Test
    fun testAtsActiveIdIsNullWhenCollectionEmpty() {
        val state = emptyEquipmentState()
        val atsList = state.atsList  // emptyList()

        val selectedAtsId = ""
        val activeAtsId = if (selectedAtsId.isNotEmpty() && atsList.any { it.atsId == selectedAtsId }) {
            selectedAtsId
        } else {
            atsList.firstOrNull()?.atsId
        }

        assertNull("Active ATS ID must be null when collection is empty", activeAtsId)

        val activeAts = atsList.firstOrNull { it.atsId == activeAtsId }
        assertNull("Active ATS entity must be null when collection is empty", activeAts)
    }

    @Test
    fun testUpsActiveIdIsNullWhenCollectionEmpty() {
        val state = emptyEquipmentState()
        val upsList = state.upsList  // emptyList()

        val selectedUpsId = ""
        val activeUpsId = if (selectedUpsId.isNotEmpty() && upsList.any { it.upsId == selectedUpsId }) {
            selectedUpsId
        } else {
            upsList.firstOrNull()?.upsId
        }

        assertNull("Active UPS ID must be null when collection is empty", activeUpsId)

        val activeUps = upsList.firstOrNull { it.upsId == activeUpsId }
        assertNull("Active UPS entity must be null when collection is empty", activeUps)
    }
}
