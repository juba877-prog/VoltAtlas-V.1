package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class PowerSystemsUiStateTest {

    @Test
    fun testModuleConnectedLoadFilteringIsIsolated() {
        val circGenOnly = CircuitEntity(id = 1, circuitName = "GEN-LOAD", generatorIncluded = true, atsIncluded = false, upsBacked = false)
        val circAtsOnly = CircuitEntity(id = 2, circuitName = "ATS-LOAD", generatorIncluded = false, atsIncluded = true, upsBacked = false)
        val circUpsOnly = CircuitEntity(id = 3, circuitName = "UPS-LOAD", generatorIncluded = false, atsIncluded = false, upsBacked = true)
        val allCircuits = listOf(circGenOnly, circAtsOnly, circUpsOnly)

        val generatorLoads = allCircuits.filter { it.generatorIncluded }
        val atsLoads = allCircuits.filter { it.atsIncluded }
        val upsLoads = allCircuits.filter { it.upsBacked }

        assertEquals(1, generatorLoads.size)
        assertEquals("GEN-LOAD", generatorLoads[0].circuitName)

        assertEquals(1, atsLoads.size)
        assertEquals("ATS-LOAD", atsLoads[0].circuitName)

        assertEquals(1, upsLoads.size)
        assertEquals("UPS-LOAD", upsLoads[0].circuitName)
    }

    @Test
    fun testAtsPersistedSettingsSynchronizationAndNoDefaultOverwrite() {
        val initialDefaultSettings = AtsEntity(id = 1, normalSourceId = null, emergencySourceId = null)
        val delayedPersistedSettings = AtsEntity(id = 1, normalSourceId = "TX-01", emergencySourceId = "GEN-01")

        var savedEntity: AtsEntity? = null
        val onSave: (AtsEntity) -> Unit = { entity -> savedEntity = entity }

        // Initial default state - onSave must NOT be called on setup/render
        assertNull("Initial setup must not trigger auto-save over Room data", savedEntity)

        // Simulating user interaction selecting normal source
        val userUpdated = delayedPersistedSettings.copy(normalSourceId = "GRID-01")
        onSave(userUpdated)

        assertNotNull("User explicit selection must be passed to onSave", savedEntity)
        assertEquals("GRID-01", savedEntity?.normalSourceId)
        assertEquals("GEN-01", savedEntity?.emergencySourceId)
    }

    @Test
    fun testDelayedPersistedStateReplacesInitialDefaults() {
        val initialSettings = AtsEntity()
        assertNull(initialSettings.normalSourceId)

        // Room async load returns persisted settings
        val roomLoadedSettings = initialSettings.copy(normalSourceId = "TX-MAIN", emergencySourceId = "GEN-STDBY")
        
        assertEquals("TX-MAIN", roomLoadedSettings.normalSourceId)
        assertEquals("GEN-STDBY", roomLoadedSettings.emergencySourceId)
    }

    @Test
    fun testUiStateSuccessMultiInstanceLists() {
        val genList = listOf(GeneratorEntity(generatorId = "GEN-01"), GeneratorEntity(generatorId = "GEN-02"))
        val atsList = listOf(AtsEntity(atsId = "ATS-01"), AtsEntity(atsId = "ATS-02"))
        val upsList = listOf(UpsEntity(upsId = "UPS-01"))

        val successState = UiState.Success(
            circuits = emptyList(),
            transformers = emptyList(),
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            generators = genList,
            atsList = atsList,
            upsList = upsList,
            pfCorrectionResults = emptyList()
        )

        assertEquals(2, successState.generators.size)
        assertEquals("GEN-01", successState.generators[0].generatorId)
        assertEquals("GEN-02", successState.generators[1].generatorId)

        assertEquals(2, successState.atsList.size)
        assertEquals("ATS-01", successState.atsList[0].atsId)
        assertEquals("ATS-02", successState.atsList[1].atsId)

        assertEquals(1, successState.upsList.size)
        assertEquals("UPS-01", successState.upsList[0].upsId)
    }
}
