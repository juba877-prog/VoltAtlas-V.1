package com.example.ui.dialogs.circuiteditor.spec

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitEntityMapper
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.primaryConstructor

/**
 * Pure JVM specification tests for [CircuitEntityMapper].
 *
 * Evidence Label: NEW_MAPPER_SPECIFIED
 */
class CircuitEntityMapperSpecTest {

    private val defaultResolution = CircuitSourceResolution(
        finalTxId = "TR-01",
        parentBoardObj = null,
        calculatedParentId = null,
        calculatedLevel = 0,
        calculatedSourceType = "TRANSFORMER"
    )

    @Test
    fun test01_editableFieldMapping_nameTrimDirectly() {
        val draft = CircuitEditorDraft(name = "  Main Feeder 1  ")
        val entity = CircuitEntityMapper.mapToEntity(draft, null, defaultResolution)

        assertEquals("Main Feeder 1", entity.circuitName)
    }

    @Test
    fun test02_derivedSupplyFlags_allFourCategories() {
        val normal = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C1", supplyCategory = "NORMAL"), null, defaultResolution
        )
        assertFalse(normal.generatorIncluded)
        assertFalse(normal.atsIncluded)
        assertFalse(normal.upsBacked)

        val emg = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C1", supplyCategory = "EMERGENCY"), null, defaultResolution
        )
        assertTrue(emg.generatorIncluded)
        assertTrue(emg.atsIncluded)
        assertFalse(emg.upsBacked)

        val ups = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C1", supplyCategory = "UPS"), null, defaultResolution
        )
        assertFalse(ups.generatorIncluded)
        assertFalse(ups.atsIncluded)
        assertTrue(ups.upsBacked)

        val emgUps = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C1", supplyCategory = "EMERGENCY + UPS"), null, defaultResolution
        )
        assertTrue(emgUps.generatorIncluded)
        assertTrue(emgUps.atsIncluded)
        assertTrue(emgUps.upsBacked)
    }

    @Test
    fun test03_sourceHierarchyMapping() {
        val resolution = CircuitSourceResolution(
            finalTxId = "TX-CUSTOM",
            parentBoardObj = null,
            calculatedParentId = 42,
            calculatedLevel = 3,
            calculatedSourceType = "PANEL"
        )
        val entity = CircuitEntityMapper.mapToEntity(CircuitEditorDraft(name = "C1"), null, resolution)

        assertEquals("TX-CUSTOM", entity.transformerId)
        assertEquals(42, entity.parentId)
        assertEquals(3, entity.level)
        assertEquals("PANEL", entity.sourceType)
    }

    @Test
    fun test04_addModeDefaults() {
        val entity = CircuitEntityMapper.mapToEntity(CircuitEditorDraft(name = "New Circuit"), null, defaultResolution)

        assertEquals(0, entity.id)
        assertTrue(entity.active)
        assertEquals("DEFAULT_PROJECT", entity.projectId)
        assertNull(entity.overrideDeviceType)
        assertNull(entity.overrideDeviceSize)
        assertNull(entity.overrideCableSize)
        assertNull(entity.overrideNeutralSize)
        assertNull(entity.overrideEarthSize)
        assertNull(entity.assignedPhase)
        assertNull(entity.diversityGroup)
        assertNull(entity.directLoadConfirmed)
        assertNull(entity.phaseImbalanceLimitOverridePct)
        assertNull(entity.scRatingKA)
    }

    @Test
    fun test05_editModeOpaqueFieldPreservation() {
        val original = CircuitEntity(
            id = 101,
            circuitName = "Original",
            projectId = "PROJECT-99",
            overrideDeviceType = "MCCB",
            overrideDeviceSize = 250.0,
            overrideCableSize = "4x120",
            overrideNeutralSize = "2x70",
            overrideEarthSize = "1x70",
            assignedPhase = "L1",
            diversityGroup = "GroupA",
            active = false,
            directLoadConfirmed = true,
            phaseImbalanceLimitOverridePct = 15.0,
            scRatingKA = 36.5
        )
        val draft = CircuitEditorDraft(name = "Updated Name")
        val entity = CircuitEntityMapper.mapToEntity(draft, original, defaultResolution)

        assertEquals(101, entity.id)
        assertEquals("Updated Name", entity.circuitName)
        assertEquals("PROJECT-99", entity.projectId)
        assertEquals("MCCB", entity.overrideDeviceType)
        assertEquals(250.0, entity.overrideDeviceSize)
        assertEquals("4x120", entity.overrideCableSize)
        assertEquals("2x70", entity.overrideNeutralSize)
        assertEquals("1x70", entity.overrideEarthSize)
        assertEquals("L1", entity.assignedPhase)
        assertEquals("GroupA", entity.diversityGroup)
        assertFalse(entity.active)
        assertEquals(true, entity.directLoadConfirmed)
        assertEquals(15.0, entity.phaseImbalanceLimitOverridePct)
        assertEquals(36.5, entity.scRatingKA)
    }

    @Test
    fun test06_conditionalEquipmentIdNulling() {
        val draftWithNone = CircuitEditorDraft(
            name = "C1",
            assignedAtsId = "None",
            assignedGeneratorId = "   ",
            assignedUpsId = ""
        )
        val entity1 = CircuitEntityMapper.mapToEntity(draftWithNone, null, defaultResolution)
        assertNull(entity1.assignedAtsId)
        assertNull(entity1.assignedGeneratorId)
        assertNull(entity1.assignedUpsId)

        val draftWithValid = CircuitEditorDraft(
            name = "C1",
            assignedAtsId = "ATS-01",
            assignedGeneratorId = "GEN-01",
            assignedUpsId = "UPS-01"
        )
        val entity2 = CircuitEntityMapper.mapToEntity(draftWithValid, null, defaultResolution)
        assertEquals("ATS-01", entity2.assignedAtsId)
        assertEquals("GEN-01", entity2.assignedGeneratorId)
        assertEquals("UPS-01", entity2.assignedUpsId)
    }

    @Test
    fun test07_motorFieldPersistenceAndNulling() {
        val motorDraft = CircuitEditorDraft(
            name = "MTR-1",
            loadNature = "Motor",
            startingMethod = "Star-Delta",
            startingCurrentMultiplier = "3.5"
        )
        val motorEntity = CircuitEntityMapper.mapToEntity(motorDraft, null, defaultResolution)
        assertEquals("Motor", motorEntity.loadNature)
        assertEquals("Star-Delta", motorEntity.startingMethod)
        assertEquals(3.5, motorEntity.startingCurrentMultiplier)

        val staticDraft = CircuitEditorDraft(
            name = "Static-1",
            loadNature = "Static",
            startingMethod = "DOL",
            startingCurrentMultiplier = "6.0"
        )
        val staticEntity = CircuitEntityMapper.mapToEntity(staticDraft, null, defaultResolution)
        assertEquals("Static", staticEntity.loadNature)
        assertNull(staticEntity.startingMethod)
        assertNull(staticEntity.startingCurrentMultiplier)
    }

    @Test
    fun test08_buswayFieldPersistenceAndNulling() {
        val buswayDraft = CircuitEditorDraft(
            name = "BW-1",
            feederType = "Busway",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC",
            buswayMfg = "Schneider",
            buswayModel = "Canalis"
        )
        val buswayEntity = CircuitEntityMapper.mapToEntity(buswayDraft, null, defaultResolution)
        assertEquals("Busway", buswayEntity.feederType)
        assertEquals("Schneider", buswayEntity.buswayMfg)
        assertEquals("Canalis", buswayEntity.buswayModel)
        assertEquals("Elsewedy", buswayEntity.manufacturer)
        assertEquals("Cu/XLPE/SWA/PVC", buswayEntity.cableType)

        val cableDraft = CircuitEditorDraft(
            name = "CB-1",
            feederType = "Cable",
            buswayMfg = "Schneider",
            buswayModel = "Canalis"
        )
        val cableEntity = CircuitEntityMapper.mapToEntity(cableDraft, null, defaultResolution)
        assertEquals("Cable", cableEntity.feederType)
        assertNull(cableEntity.buswayMfg)
        assertNull(cableEntity.buswayModel)
    }

    @Test
    fun test09_numericParsingCoercionAndPercentageConversion() {
        val draft = CircuitEditorDraft(
            name = "C1",
            loadKVA = "-10.0",
            pf = "0.05",
            demandFactor = "1.5",
            safetyFactor = "0.8",
            voltageV = "invalid",
            parallelRuns = "invalid",
            maxVDpct = "3.0",
            airTempC = "invalid",
            gndTempC = "invalid",
            burialDepth_m = "invalid",
            soilRes = "invalid",
            noCircuits = "invalid",
            length_m = "invalid"
        )
        val entity = CircuitEntityMapper.mapToEntity(draft, null, defaultResolution)

        assertEquals(0.0, entity.loadKVA, 0.001)
        assertEquals(0.1, entity.pf, 0.001) // coerced in [0.1, 1.0]
        assertEquals(1.0, entity.demandFactor, 0.001) // coerced in [0.0, 1.0]
        assertEquals(1.0, entity.safetyFactor, 0.001) // max(1.0, ...)
        assertEquals(400, entity.voltageV) // fallback
        assertEquals(1, entity.parallelRuns) // fallback
        assertEquals(0.03, entity.maxVDpct, 0.0001) // 3.0% -> 0.03
        assertEquals(40, entity.airTempC) // fallback
        assertEquals(40, entity.gndTempC) // fallback
        assertEquals(0.6, entity.burialDepth_m, 0.001) // fallback
        assertEquals(125, entity.soilRes) // fallback
        assertEquals(1, entity.noCircuits) // fallback
        assertEquals(30.0, entity.length_m, 0.001) // fallback
    }

    @Test
    fun test10_panelHeaderParsingAndNulling() {
        val draft = CircuitEditorDraft(
            name = "DB-1",
            panelType = "DB",
            busbarRatingA = "630.0",
            mainIncomingType = "MCCB",
            mainIncomingCount = "1",
            ipRating = "IP54",
            panelLocation = "Basement",
            cableEntry = "Bottom",
            numberOfWays = "24",
            feederDescription = "Main Incomer Feeder"
        )
        val entity = CircuitEntityMapper.mapToEntity(draft, null, defaultResolution)

        assertEquals(630.0, entity.busbarRatingA)
        assertEquals("MCCB", entity.mainIncomingType)
        assertEquals(1, entity.mainIncomingCount)
        assertEquals("IP54", entity.ipRating)
        assertEquals("Basement", entity.panelLocation)
        assertEquals("Bottom", entity.cableEntry)
        assertEquals(24, entity.numberOfWays)
        assertEquals("Main Incomer Feeder", entity.feederDescription)
    }

    @Test
    fun test11_mechanical62PropertyClassificationCheck() {
        val params = CircuitEntity::class.primaryConstructor!!.parameters
        assertEquals(62, params.size)
        val names = params.map { it.name }.toSet()
        assertEquals(62, names.size)
    }
}
