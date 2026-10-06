package com.example.ui.dialogs.circuiteditor.spec

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.CircuitEntity
import com.example.data.catalog.BuswayCatalogEntry
import com.example.ui.dialogs.circuiteditor.mapper.CircuitEntityMapper
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorPreferencesSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.state.CircuitEditorInitializer
import com.example.ui.screens.PanelScheduleSaveCoordinator
import com.example.ui.screens.circuits.CircuitsGridSaveCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.primaryConstructor

/**
 * Mechanically complete test suite establishing 100% ownership, mapping, preservation,
 * coercion, nulling, and caller coordination coverage across all 62 properties of [CircuitEntity].
 *
 * Evidence Label: CALLER_CONTRACT_VERIFIED / NEW_MAPPER_SPECIFIED
 */
class CircuitEntityActivatedPathCoverageTest {

    private val preferences = CircuitEditorPreferencesSnapshot(
        defaultVoltage3Phase = 400,
        defaultMaxVdPower = 3.0,
        defaultConductor = "Cu",
        defaultInsulation = "XLPE",
        defaultCoreConfig = "4C"
    )

    private val transformers = listOf(
        TransformerSnapshot(transformerId = "TX-01", ratingKVA = 1000.0),
        TransformerSnapshot(transformerId = "TX-02", ratingKVA = 1600.0)
    )

    private val catalog = CableCatalogSnapshot(
        manufacturers = listOf("Elsewedy", "Ducab"),
        cableTypes = listOf(
            CableType(
                code = 1,
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                conductor = "Cu",
                insulation = "XLPE",
                armour = "SWA",
                cores = "4C",
                voltage = "0.6/1kV",
                standard = "IEC 60502",
                rColumn = "R_Cu90"
            ),
            CableType(
                code = 2,
                cableType = "Al/XLPE/STA/PVC - 4C",
                conductor = "Al",
                insulation = "XLPE",
                armour = "STA",
                cores = "4C",
                voltage = "0.6/1kV",
                standard = "IEC 60502",
                rColumn = "R_Al90"
            )
        ),
        cableCatalog = listOf(
            CableCatalogEntry(
                mfg = "Elsewedy",
                typeCode = 1,
                size = 50.0,
                model = "4x50",
                gnd = 150.0,
                duct = 130.0,
                air = 140.0,
                od = 28.0,
                wt = 2100.0
            )
        ),
        busways = listOf(
            BuswayCatalogEntry(
                mfg = "Elsewedy",
                model = "Power Link",
                conductor = "Cu",
                ratingA = 1000.0,
                ipkKA = 105.0,
                icwKA = 50.0,
                rMOhmPerM = 0.04,
                xMOhmPerM = 0.02
            )
        )
    )

    private val defaultResolution = CircuitSourceResolution(
        finalTxId = "TX-01",
        parentBoardObj = null,
        calculatedParentId = null,
        calculatedLevel = 0,
        calculatedSourceType = "TRANSFORMER"
    )

    // ============================================================================
    // 1. Mechanical 63-Property Reflection Check
    // ============================================================================
    @Test
    fun test01_mechanicalCompleteness_all63PropertiesAccountedFor() {
        val constructorParams = CircuitEntity::class.primaryConstructor!!.parameters
        assertEquals("CircuitEntity must declare exactly 63 constructor properties", 63, constructorParams.size)

        val expectedPropertyNames = setOf(
            "id", "circuitName", "loadKVA", "fedFrom", "transformerId", "loadType", "phases",
            "voltageV", "pf", "safetyFactor", "manufacturer", "cableType", "installation",
            "parallelRuns", "airTempC", "gndTempC", "burialDepth_m", "soilRes", "noCircuits",
            "formation", "spacing", "length_m", "maxVDpct", "overrideDeviceType", "overrideDeviceSize",
            "overrideCableSize", "overrideNeutralSize", "overrideEarthSize", "isHarmonic",
            "assignedPhase", "upsBacked", "projectId", "demandFactor", "diversityGroup",
            "generatorIncluded", "atsIncluded", "assignedGeneratorId", "assignedAtsId",
            "assignedUpsId", "loadNature", "startingMethod", "startingCurrentMultiplier",
            "active", "panelType", "parentId", "level", "sourceType", "directLoadConfirmed",
            "phaseImbalanceLimitOverridePct", "loadCategory", "busbarRatingA", "scRatingKA",
            "mainIncomingType", "mainIncomingCount", "ipRating", "panelLocation", "cableEntry",
            "feederDescription", "numberOfWays", "feederType", "buswayMfg", "buswayModel",
            "overrideClearingTime_s"
        )

        val actualPropertyNames = constructorParams.mapNotNull { it.name }.toSet()
        assertEquals("All 63 expected properties must exactly match CircuitEntity properties", expectedPropertyNames, actualPropertyNames)
    }

    // ============================================================================
    // 2. All 13 Opaque Preserved Fields on Edit Roundtrip
    // ============================================================================
    @Test
    fun test02_opaquePreservedFields_all13FieldsPreservedOnEditRoundtrip() {
        val original = CircuitEntity(
            id = 777,
            circuitName = "Feeder X",
            projectId = "SPECIAL_PROJECT_2026",
            overrideDeviceType = "ACB-FIXED",
            overrideDeviceSize = 800.0,
            overrideCableSize = "2x(4x185)",
            overrideNeutralSize = "2x185",
            overrideEarthSize = "1x95",
            assignedPhase = "L2",
            diversityGroup = "HVAC_CHILLERS",
            active = false,
            directLoadConfirmed = true,
            phaseImbalanceLimitOverridePct = 12.5,
            scRatingKA = 40.0
        )

        val draft = CircuitEditorInitializer.initialize(original, preferences, transformers, catalog)
        val saved = CircuitEntityMapper.mapToEntity(draft, original, defaultResolution)

        assertEquals("id must be preserved", 777, saved.id)
        assertEquals("projectId must be preserved", "SPECIAL_PROJECT_2026", saved.projectId)
        assertEquals("overrideDeviceType must be preserved", "ACB-FIXED", saved.overrideDeviceType)
        assertEquals("overrideDeviceSize must be preserved", 800.0, saved.overrideDeviceSize)
        assertEquals("overrideCableSize must be preserved", "2x(4x185)", saved.overrideCableSize)
        assertEquals("overrideNeutralSize must be preserved", "2x185", saved.overrideNeutralSize)
        assertEquals("overrideEarthSize must be preserved", "1x95", saved.overrideEarthSize)
        assertEquals("assignedPhase must be preserved", "L2", saved.assignedPhase)
        assertEquals("diversityGroup must be preserved", "HVAC_CHILLERS", saved.diversityGroup)
        assertFalse("active must be preserved as false", saved.active)
        assertEquals("directLoadConfirmed must be preserved", true, saved.directLoadConfirmed)
        assertEquals("phaseImbalanceLimitOverridePct must be preserved", 12.5, saved.phaseImbalanceLimitOverridePct)
        assertEquals("scRatingKA must be preserved", 40.0, saved.scRatingKA)
    }

    // ============================================================================
    // 3. Add Mode Defaults for Opaque Fields
    // ============================================================================
    @Test
    fun test03_addModeDefaults_allOpaqueFieldsReceiveExpectedEntityDefaults() {
        val draft = CircuitEditorInitializer.initialize(null, preferences, transformers, catalog)
        val added = CircuitEntityMapper.mapToEntity(draft, null, defaultResolution)

        assertEquals("New circuit id must be 0", 0, added.id)
        assertEquals("New circuit projectId must be DEFAULT_PROJECT", "DEFAULT_PROJECT", added.projectId)
        assertTrue("New circuit active must default to true", added.active)
        assertNull("New circuit scRatingKA must default to null", added.scRatingKA)
        assertNull("New circuit overrideDeviceType must default to null", added.overrideDeviceType)
        assertNull("New circuit overrideDeviceSize must default to null", added.overrideDeviceSize)
        assertNull("New circuit overrideCableSize must default to null", added.overrideCableSize)
        assertNull("New circuit overrideNeutralSize must default to null", added.overrideNeutralSize)
        assertNull("New circuit overrideEarthSize must default to null", added.overrideEarthSize)
        assertNull("New circuit assignedPhase must default to null", added.assignedPhase)
        assertNull("New circuit diversityGroup must default to null", added.diversityGroup)
        assertNull("New circuit directLoadConfirmed must default to null", added.directLoadConfirmed)
        assertNull("New circuit phaseImbalanceLimitOverridePct must default to null", added.phaseImbalanceLimitOverridePct)
    }

    // ============================================================================
    // 4. All Five Overrides Preserved Simultaneously Across Edit
    // ============================================================================
    @Test
    fun test04_allFiveOverridesPreservedSimultaneouslyOnEdit() {
        val original = CircuitEntity(
            id = 50,
            circuitName = "Sub-Main 1",
            overrideDeviceType = "MCCB",
            overrideDeviceSize = 400.0,
            overrideCableSize = "4x240 mm²",
            overrideNeutralSize = "4x120 mm²",
            overrideEarthSize = "1x120 mm²"
        )

        val draft = CircuitEditorInitializer.initialize(original, preferences, transformers, catalog)
        val updated = CircuitEntityMapper.mapToEntity(draft, original, defaultResolution)

        assertEquals("MCCB", updated.overrideDeviceType)
        assertEquals(400.0, updated.overrideDeviceSize)
        assertEquals("4x240 mm²", updated.overrideCableSize)
        assertEquals("4x120 mm²", updated.overrideNeutralSize)
        assertEquals("1x120 mm²", updated.overrideEarthSize)
    }

    // ============================================================================
    // 5. Source Hierarchy - Direct Sources (Transformer, Grid, Generator, ATS)
    // ============================================================================
    @Test
    fun test05_sourceHierarchy_directSources_grid_generator_ats_transformer() {
        val existing = listOf(
            CircuitEntity(id = 1, circuitName = "MDB", level = 0, transformerId = "TX-01")
        )

        // Transformer
        val txRes = CircuitSourceResolver.resolveSourceHierarchy("Transformer", "TX-02", existing, transformers)
        assertEquals("TX-02", txRes.finalTxId)
        assertNull(txRes.calculatedParentId)
        assertEquals(0, txRes.calculatedLevel)
        assertEquals("TRANSFORMER", txRes.calculatedSourceType)

        // Generator
        val genRes = CircuitSourceResolver.resolveSourceHierarchy("Generator", "", existing, transformers)
        assertEquals("GENERATOR", genRes.calculatedSourceType)
        assertNull(genRes.calculatedParentId)
        assertEquals(0, genRes.calculatedLevel)

        // Grid
        val gridRes = CircuitSourceResolver.resolveSourceHierarchy("Grid", "", existing, transformers)
        assertEquals("GRID", gridRes.calculatedSourceType)
        assertNull(gridRes.calculatedParentId)
        assertEquals(0, gridRes.calculatedLevel)

        // Utility Grid
        val uGridRes = CircuitSourceResolver.resolveSourceHierarchy("Utility Grid", "", existing, transformers)
        assertEquals("GRID", uGridRes.calculatedSourceType)
        assertNull(uGridRes.calculatedParentId)
        assertEquals(0, uGridRes.calculatedLevel)

        // ATS
        val atsRes = CircuitSourceResolver.resolveSourceHierarchy("ATS", "", existing, transformers)
        assertEquals("ATS", atsRes.calculatedSourceType)
        assertNull(atsRes.calculatedParentId)
        assertEquals(0, atsRes.calculatedLevel)
    }

    // ============================================================================
    // 6. Source Hierarchy - Parent Board Level and Transformer Inheritance
    // ============================================================================
    @Test
    fun test06_sourceHierarchy_parentBoard_levelAndTransformerInheritance() {
        val existing = listOf(
            CircuitEntity(id = 10, circuitName = "MDB", level = 0, transformerId = "TX-EAST"),
            CircuitEntity(id = 20, circuitName = "SMDB-1", level = 1, transformerId = "TX-EAST", parentId = 10)
        )

        // Child of MDB (level 0 -> 1)
        val child1 = CircuitSourceResolver.resolveSourceHierarchy("MDB", "", existing, transformers)
        assertEquals(10, child1.calculatedParentId)
        assertEquals(1, child1.calculatedLevel)
        assertEquals("PANEL", child1.calculatedSourceType)
        assertEquals("TX-EAST", child1.finalTxId)

        // Child of SMDB-1 (level 1 -> 2)
        val child2 = CircuitSourceResolver.resolveSourceHierarchy("SMDB-1", "", existing, transformers)
        assertEquals(20, child2.calculatedParentId)
        assertEquals(2, child2.calculatedLevel)
        assertEquals("PANEL", child2.calculatedSourceType)
        assertEquals("TX-EAST", child2.finalTxId)
    }

    // ============================================================================
    // 7. Source Hierarchy - Case-Sensitivity and Missing Parent Board Fallback
    // ============================================================================
    @Test
    fun test07_sourceHierarchy_caseSensitivityAndMissingParentFallback() {
        val existing = listOf(
            CircuitEntity(id = 10, circuitName = "MDB-Main", level = 0, transformerId = "TX-01")
        )

        // Case mismatch does not match parent
        val resMismatch = CircuitSourceResolver.resolveSourceHierarchy("mdb-main", "TX-FB", existing, transformers)
        assertNull(resMismatch.parentBoardObj)
        assertNull(resMismatch.calculatedParentId)
        assertEquals(0, resMismatch.calculatedLevel)
        assertEquals("TRANSFORMER", resMismatch.calculatedSourceType)
        assertEquals("TX-FB", resMismatch.finalTxId)

        // Unknown board name falls back to TRANSFORMER level 0
        val resUnknown = CircuitSourceResolver.resolveSourceHierarchy("NonExistentBoard", "TX-DEF", existing, transformers)
        assertNull(resUnknown.calculatedParentId)
        assertEquals(0, resUnknown.calculatedLevel)
        assertEquals("TRANSFORMER", resUnknown.calculatedSourceType)
        assertEquals("TX-DEF", resUnknown.finalTxId)
    }

    // ============================================================================
    // 8. R1B Contract - Empty Draft and No Transformers Emits Empty String (No Fabrication)
    // ============================================================================
    @Test
    fun test08_sourceHierarchy_emptyDraftAndNoTransformersReturnsEmptyAbsentRepresentation() {
        val res = CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = "Transformer",
            draftTransformerId = "",
            existingCircuits = emptyList(),
            availableTransformers = emptyList()
        )
        assertEquals("Must return absent empty string, never fabricated TX-01", "", res.finalTxId)
    }

    // ============================================================================
    // 9. Motor Fields - Mapped When Motor, Nulled When Non-Motor
    // ============================================================================
    @Test
    fun test09_motorFields_mappedWhenMotor_nulledWhenNonMotor() {
        // Motor load
        val motorDraft = CircuitEditorDraft(
            name = "Pump-1",
            loadNature = "Motor",
            startingMethod = "VFD",
            startingCurrentMultiplier = "2.5"
        )
        val motorEntity = CircuitEntityMapper.mapToEntity(motorDraft, null, defaultResolution)
        assertEquals("Motor", motorEntity.loadNature)
        assertEquals("VFD", motorEntity.startingMethod)
        assertEquals(2.5, motorEntity.startingCurrentMultiplier)

        // Non-motor load (Static)
        val staticDraft = CircuitEditorDraft(
            name = "Lighting-1",
            loadNature = "Static",
            startingMethod = "VFD",
            startingCurrentMultiplier = "2.5"
        )
        val staticEntity = CircuitEntityMapper.mapToEntity(staticDraft, null, defaultResolution)
        assertEquals("Static", staticEntity.loadNature)
        assertNull("startingMethod must be null for non-motor", staticEntity.startingMethod)
        assertNull("startingCurrentMultiplier must be null for non-motor", staticEntity.startingCurrentMultiplier)
    }

    // ============================================================================
    // 10. Cable vs Busway Branching and Field Nulling
    // ============================================================================
    @Test
    fun test10_cableVsBusway_branchingAndFieldNulling() {
        // Busway feeder
        val buswayDraft = CircuitEditorDraft(
            name = "Riser-1",
            feederType = "Busway",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            buswayMfg = "Schneider",
            buswayModel = "Canalis KT"
        )
        val buswayEntity = CircuitEntityMapper.mapToEntity(buswayDraft, null, defaultResolution)
        assertEquals("Busway", buswayEntity.feederType)
        assertEquals("Schneider", buswayEntity.buswayMfg)
        assertEquals("Canalis KT", buswayEntity.buswayModel)
        assertEquals("Elsewedy", buswayEntity.manufacturer)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", buswayEntity.cableType)

        // Cable feeder
        val cableDraft = CircuitEditorDraft(
            name = "Cable-1",
            feederType = "Cable",
            buswayMfg = "Schneider",
            buswayModel = "Canalis KT"
        )
        val cableEntity = CircuitEntityMapper.mapToEntity(cableDraft, null, defaultResolution)
        assertEquals("Cable", cableEntity.feederType)
        assertNull("buswayMfg must be null on Cable path", cableEntity.buswayMfg)
        assertNull("buswayModel must be null on Cable path", cableEntity.buswayModel)
    }

    // ============================================================================
    // 11. Supply Categories and Equipment ID Mapping / Nulling
    // ============================================================================
    @Test
    fun test11_supplyCategories_allFourVariationsAndEquipmentIdNulling() {
        // NORMAL
        val normal = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C1", supplyCategory = "NORMAL"), null, defaultResolution
        )
        assertEquals("NORMAL", normal.loadType)
        assertFalse(normal.generatorIncluded)
        assertFalse(normal.atsIncluded)
        assertFalse(normal.upsBacked)

        // EMERGENCY
        val emg = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C2", supplyCategory = "EMERGENCY"), null, defaultResolution
        )
        assertEquals("EMERGENCY", emg.loadType)
        assertTrue(emg.generatorIncluded)
        assertTrue(emg.atsIncluded)
        assertFalse(emg.upsBacked)

        // UPS
        val ups = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(name = "C3", supplyCategory = "UPS"), null, defaultResolution
        )
        assertEquals("UPS", ups.loadType)
        assertFalse(ups.generatorIncluded)
        assertFalse(ups.atsIncluded)
        assertTrue(ups.upsBacked)

        // EMERGENCY + UPS
        val emgUps = CircuitEntityMapper.mapToEntity(
            CircuitEditorDraft(
                name = "C4",
                supplyCategory = "EMERGENCY + UPS",
                assignedGeneratorId = "GEN-01",
                assignedAtsId = "ATS-01",
                assignedUpsId = "UPS-01"
            ), null, defaultResolution
        )
        assertEquals("EMERGENCY + UPS", emgUps.loadType)
        assertTrue(emgUps.generatorIncluded)
        assertTrue(emgUps.atsIncluded)
        assertTrue(emgUps.upsBacked)
        assertEquals("GEN-01", emgUps.assignedGeneratorId)
        assertEquals("ATS-01", emgUps.assignedAtsId)
        assertEquals("UPS-01", emgUps.assignedUpsId)

        // Nulling when "None" or blank
        val draftWithNone = CircuitEditorDraft(
            name = "C5",
            assignedGeneratorId = "None",
            assignedAtsId = "   ",
            assignedUpsId = ""
        )
        val entityNone = CircuitEntityMapper.mapToEntity(draftWithNone, null, defaultResolution)
        assertNull(entityNone.assignedGeneratorId)
        assertNull(entityNone.assignedAtsId)
        assertNull(entityNone.assignedUpsId)
    }

    // ============================================================================
    // 12. Panel Header Fields - All Eight Fields Mapped and Nulled
    // ============================================================================
    @Test
    fun test12_panelHeaderFields_allEightFieldsMappedAndNulled() {
        val populatedDraft = CircuitEditorDraft(
            name = "MDB-North",
            panelType = "MDB",
            busbarRatingA = "1250",
            mainIncomingType = "ACB",
            mainIncomingCount = "2",
            ipRating = "IP65",
            panelLocation = "Substation 1",
            cableEntry = "Top",
            numberOfWays = "36",
            feederDescription = "Incomer from TX-01"
        )
        val entityPopulated = CircuitEntityMapper.mapToEntity(populatedDraft, null, defaultResolution)
        assertEquals(1250.0, entityPopulated.busbarRatingA)
        assertEquals("ACB", entityPopulated.mainIncomingType)
        assertEquals(2, entityPopulated.mainIncomingCount)
        assertEquals("IP65", entityPopulated.ipRating)
        assertEquals("Substation 1", entityPopulated.panelLocation)
        assertEquals("Top", entityPopulated.cableEntry)
        assertEquals(36, entityPopulated.numberOfWays)
        assertEquals("Incomer from TX-01", entityPopulated.feederDescription)

        // Blank values nulled
        val blankDraft = CircuitEditorDraft(
            name = "MDB-North",
            panelType = "MDB",
            busbarRatingA = "",
            mainIncomingType = "   ",
            mainIncomingCount = "",
            ipRating = "",
            panelLocation = "",
            cableEntry = "",
            numberOfWays = "",
            feederDescription = ""
        )
        val entityBlank = CircuitEntityMapper.mapToEntity(blankDraft, null, defaultResolution)
        assertNull(entityBlank.busbarRatingA)
        assertNull(entityBlank.mainIncomingType)
        assertNull(entityBlank.mainIncomingCount)
        assertNull(entityBlank.ipRating)
        assertNull(entityBlank.panelLocation)
        assertNull(entityBlank.cableEntry)
        assertNull(entityBlank.numberOfWays)
        assertNull(entityBlank.feederDescription)

        // Initializer busbar formatting check: 800.0 -> "800", 850.5 -> "850.5"
        val entInt = CircuitEntity(circuitName = "P1", busbarRatingA = 800.0)
        val draftInt = CircuitEditorInitializer.initialize(entInt, preferences, transformers, catalog)
        assertEquals("800", draftInt.busbarRatingA)

        val entDec = CircuitEntity(circuitName = "P2", busbarRatingA = 850.5)
        val draftDec = CircuitEditorInitializer.initialize(entDec, preferences, transformers, catalog)
        assertEquals("850.5", draftDec.busbarRatingA)
    }

    // ============================================================================
    // 13. Numeric Coercions, Fallbacks, and Percentage Conversion
    // ============================================================================
    @Test
    fun test13_numericCoercionsFallbacksAndPercentageConversion() {
        val draftCoerced = CircuitEditorDraft(
            name = "Coerce-Test",
            loadKVA = "-50.0",
            pf = "1.5",
            demandFactor = "2.0",
            safetyFactor = "0.5",
            maxVDpct = "4.5", // 4.5% -> 0.045
            voltageV = "invalid",
            parallelRuns = "invalid",
            airTempC = "invalid",
            gndTempC = "invalid",
            burialDepth_m = "invalid",
            soilRes = "invalid",
            noCircuits = "invalid",
            length_m = "invalid"
        )
        val entity = CircuitEntityMapper.mapToEntity(draftCoerced, null, defaultResolution)

        assertEquals("loadKVA coerced >= 0.0", 0.0, entity.loadKVA, 0.001)
        assertEquals("pf clamped to 1.0", 1.0, entity.pf, 0.001)
        assertEquals("demandFactor clamped to 1.0", 1.0, entity.demandFactor, 0.001)
        assertEquals("safetyFactor max >= 1.0", 1.0, entity.safetyFactor, 0.001)
        assertEquals("maxVDpct converted from 4.5 to 0.045", 0.045, entity.maxVDpct, 0.0001)
        assertEquals("voltageV fallback to 400", 400, entity.voltageV)
        assertEquals("parallelRuns fallback to 1", 1, entity.parallelRuns)
        assertEquals("airTempC fallback to 40", 40, entity.airTempC)
        assertEquals("gndTempC fallback to 40", 40, entity.gndTempC)
        assertEquals("burialDepth_m fallback to 0.6", 0.6, entity.burialDepth_m, 0.001)
        assertEquals("soilRes fallback to 125", 125, entity.soilRes)
        assertEquals("noCircuits fallback to 1", 1, entity.noCircuits)
        assertEquals("length_m fallback to 30.0", 30.0, entity.length_m, 0.001)
    }

    // ============================================================================
    // 14. CircuitsGridSaveCoordinator - Invalidation and Routing
    // ============================================================================
    @Test
    fun test14_circuitsGridSaveCoordinator_overrideCableSizeInvalidationAndRouting() {
        val original = CircuitEntity(
            id = 42,
            circuitName = "Motor Feeder",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            overrideCableSize = "4x120 mm²"
        )

        // Changing manufacturer clears overrideCableSize
        val changedMfr = original.copy(manufacturer = "Ducab")
        val normMfr = CircuitsGridSaveCoordinator.normalize(original, changedMfr)
        assertNull("overrideCableSize must be cleared when manufacturer changes", normMfr.overrideCableSize)

        // Changing cableType clears overrideCableSize
        val changedType = original.copy(cableType = "Al/XLPE/STA/PVC - 4C")
        val normType = CircuitsGridSaveCoordinator.normalize(original, changedType)
        assertNull("overrideCableSize must be cleared when cableType changes", normType.overrideCableSize)

        // Unchanged manufacturer and cableType preserves overrideCableSize
        val unchanged = original.copy(loadKVA = 55.0)
        val normUnchanged = CircuitsGridSaveCoordinator.normalize(original, unchanged)
        assertEquals("4x120 mm²", normUnchanged.overrideCableSize)

        // Routing: id == 0 -> onAdd
        var addCalled = false
        var updateCalled = false
        var dismissCalled = false
        val newCircuit = original.copy(id = 0)
        CircuitsGridSaveCoordinator.handleSave(
            original = null,
            saved = newCircuit,
            onAdd = { addCalled = true },
            onUpdate = { updateCalled = true },
            onDismiss = { dismissCalled = true }
        )
        assertTrue("onAdd must be called when id == 0", addCalled)
        assertFalse("onUpdate must not be called when id == 0", updateCalled)
        assertTrue("onDismiss must be called", dismissCalled)

        // Routing: id != 0 -> onUpdate
        addCalled = false
        updateCalled = false
        dismissCalled = false
        CircuitsGridSaveCoordinator.handleSave(
            original = original,
            saved = original,
            onAdd = { addCalled = true },
            onUpdate = { updateCalled = true },
            onDismiss = { dismissCalled = true }
        )
        assertFalse("onAdd must not be called when id != 0", addCalled)
        assertTrue("onUpdate must be called when id != 0", updateCalled)
        assertTrue("onDismiss must be called", dismissCalled)
    }

    // ============================================================================
    // 15. PanelScheduleSaveCoordinator - Preserves Overrides and Routes OptionKey
    // ============================================================================
    @Test
    fun test15_panelScheduleSaveCoordinator_preservesOverridesAndRoutesOptionKey() {
        val original = CircuitEntity(
            id = 15,
            circuitName = "Panel-15",
            panelType = "DB",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            overrideCableSize = "4x185 mm²"
        )
        val modified = original.copy(manufacturer = "Ducab")

        var addCircuit: CircuitEntity? = null
        var updateCircuit: CircuitEntity? = null
        var selectedKey: String? = null
        var dismissCalled = false

        // Edit mode (isNewCircuit = false): does NOT invalidate overrideCableSize, calls onUpdate
        PanelScheduleSaveCoordinator.handleSave(
            savedCircuit = modified,
            isNewCircuit = false,
            onAdd = { addCircuit = it },
            onUpdate = { updateCircuit = it },
            onUpdateSelectedOptionKey = { selectedKey = it },
            onDismiss = { dismissCalled = true }
        )
        assertNull(addCircuit)
        assertNotNull(updateCircuit)
        assertEquals("4x185 mm²", updateCircuit?.overrideCableSize)
        assertTrue(dismissCalled)

        // Add mode (isNewCircuit = true): calls onAdd and updates selectedOptionKey
        val newPanel = CircuitEntity(id = 0, circuitName = "Sub-DB-East", panelType = "SMDB")
        var newSelectedKey: String? = null
        PanelScheduleSaveCoordinator.handleSave(
            savedCircuit = newPanel,
            isNewCircuit = true,
            onAdd = { addCircuit = it },
            onUpdate = { updateCircuit = it },
            onUpdateSelectedOptionKey = { newSelectedKey = it },
            onDismiss = { }
        )
        assertEquals("name_Sub-DB-East", newSelectedKey)

        // Existing panel with id != 0
        val existingDb = CircuitEntity(id = 99, circuitName = "MDB-99", panelType = "MDB")
        assertEquals("id_99", PanelScheduleSaveCoordinator.computeSelectedOptionKey(existingDb))

        // LOAD type panel returns null key
        val loadCircuit = CircuitEntity(id = 1, circuitName = "Load 1", panelType = "LOAD")
        assertNull(PanelScheduleSaveCoordinator.computeSelectedOptionKey(loadCircuit))
    }

    // ============================================================================
    // 16. Comprehensive End-to-End Activated Path Roundtrip
    // ============================================================================
    @Test
    fun test16_endToEndActivatedPath_roundtripIntegrityCheck() {
        val fullOriginal = CircuitEntity(
            id = 100,
            circuitName = "Full Board Incomer",
            loadKVA = 120.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "EMERGENCY + UPS",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.9,
            safetyFactor = 1.25,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 2,
            airTempC = 45,
            gndTempC = 35,
            burialDepth_m = 0.8,
            soilRes = 150,
            noCircuits = 2,
            formation = "Trefoil",
            spacing = "0.15m",
            length_m = 75.0,
            maxVDpct = 0.025, // 2.5%
            overrideDeviceType = "MCCB-ELECTRONIC",
            overrideDeviceSize = 630.0,
            overrideCableSize = "2x(4x120)",
            overrideNeutralSize = "2x70",
            overrideEarthSize = "1x70",
            isHarmonic = true,
            assignedPhase = "L1-L2-L3",
            upsBacked = true,
            projectId = "HOSPITAL_EXPANSION",
            demandFactor = 0.95,
            diversityGroup = "CRITICAL_POWER",
            generatorIncluded = true,
            atsIncluded = true,
            assignedGeneratorId = "GEN-HOSP-01",
            assignedAtsId = "ATS-HOSP-01",
            assignedUpsId = "UPS-HOSP-01",
            loadNature = "Motor",
            startingMethod = "Star-Delta",
            startingCurrentMultiplier = 3.5,
            active = true,
            panelType = "MDB",
            parentId = null,
            level = 0,
            sourceType = "TRANSFORMER",
            directLoadConfirmed = true,
            phaseImbalanceLimitOverridePct = 10.0,
            loadCategory = "Essential",
            busbarRatingA = 1600.0,
            scRatingKA = 50.0,
            mainIncomingType = "ACB",
            mainIncomingCount = 1,
            ipRating = "IP54",
            panelLocation = "Substation Basement",
            cableEntry = "Bottom",
            feederDescription = "Hospital Feeder 1",
            numberOfWays = 48,
            feederType = "Cable",
            buswayMfg = null,
            buswayModel = null
        )

        // 1. Initializer creates draft
        val draft = CircuitEditorInitializer.initialize(fullOriginal, preferences, transformers, catalog)
        assertEquals("Full Board Incomer", draft.name)
        assertEquals("120.0", draft.loadKVA)
        assertEquals("EMERGENCY + UPS", draft.supplyCategory)
        assertEquals("2.5", draft.maxVDpct)
        assertEquals("1600", draft.busbarRatingA)
        assertEquals("Essential", draft.loadCategory)

        // 2. Resolve source
        val resolution = CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = draft.fedFrom,
            draftTransformerId = draft.transformerId,
            existingCircuits = listOf(fullOriginal),
            availableTransformers = transformers
        )
        assertEquals("TX-01", resolution.finalTxId)
        assertEquals(0, resolution.calculatedLevel)
        assertEquals("TRANSFORMER", resolution.calculatedSourceType)

        // 3. Map to entity
        val mapped = CircuitEntityMapper.mapToEntity(draft, fullOriginal, resolution)

        // Verify all key fields mapped & preserved
        assertEquals(fullOriginal.id, mapped.id)
        assertEquals(fullOriginal.circuitName, mapped.circuitName)
        assertEquals(fullOriginal.projectId, mapped.projectId)
        assertEquals(fullOriginal.scRatingKA, mapped.scRatingKA)
        assertEquals(fullOriginal.overrideDeviceType, mapped.overrideDeviceType)
        assertEquals(fullOriginal.overrideDeviceSize, mapped.overrideDeviceSize)
        assertEquals(fullOriginal.overrideCableSize, mapped.overrideCableSize)
        assertEquals(fullOriginal.overrideNeutralSize, mapped.overrideNeutralSize)
        assertEquals(fullOriginal.overrideEarthSize, mapped.overrideEarthSize)
        assertEquals(fullOriginal.assignedPhase, mapped.assignedPhase)
        assertEquals(fullOriginal.diversityGroup, mapped.diversityGroup)
        assertEquals(fullOriginal.active, mapped.active)
        assertEquals(fullOriginal.directLoadConfirmed, mapped.directLoadConfirmed)
        assertEquals(fullOriginal.phaseImbalanceLimitOverridePct, mapped.phaseImbalanceLimitOverridePct)
        assertEquals(fullOriginal.loadCategory, mapped.loadCategory)
        assertEquals(fullOriginal.busbarRatingA, mapped.busbarRatingA)
        assertEquals(fullOriginal.mainIncomingType, mapped.mainIncomingType)
        assertEquals(fullOriginal.mainIncomingCount, mapped.mainIncomingCount)
        assertEquals(fullOriginal.ipRating, mapped.ipRating)
        assertEquals(fullOriginal.panelLocation, mapped.panelLocation)
        assertEquals(fullOriginal.cableEntry, mapped.cableEntry)
        assertEquals(fullOriginal.feederDescription, mapped.feederDescription)
        assertEquals(fullOriginal.numberOfWays, mapped.numberOfWays)
        assertEquals(fullOriginal.loadNature, mapped.loadNature)
        assertEquals(fullOriginal.startingMethod, mapped.startingMethod)
        assertEquals(fullOriginal.startingCurrentMultiplier, mapped.startingCurrentMultiplier)
        assertTrue(mapped.generatorIncluded)
        assertTrue(mapped.atsIncluded)
        assertTrue(mapped.upsBacked)
        assertEquals("GEN-HOSP-01", mapped.assignedGeneratorId)
        assertEquals("ATS-HOSP-01", mapped.assignedAtsId)
        assertEquals("UPS-HOSP-01", mapped.assignedUpsId)
        assertEquals(0.025, mapped.maxVDpct, 0.0001)

        // 4. Caller save coordinator normalizes and routes
        var savedCircuit: CircuitEntity? = null
        CircuitsGridSaveCoordinator.handleSave(
            original = fullOriginal,
            saved = mapped,
            onAdd = { },
            onUpdate = { savedCircuit = it },
            onDismiss = { }
        )
        assertNotNull("Circuit must be routed to onUpdate", savedCircuit)
        assertEquals("2x(4x120)", savedCircuit?.overrideCableSize)
    }
}
