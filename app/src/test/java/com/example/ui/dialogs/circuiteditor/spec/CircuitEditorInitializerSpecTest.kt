package com.example.ui.dialogs.circuiteditor.spec

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.catalog.BuswayCatalogEntry
import com.example.testing.CatalogStateResetRule
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorPreferencesSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.state.CircuitEditorInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Pure JVM specification tests for [CircuitEditorInitializer].
 *
 * Evidence Label: NEW_MAPPER_SPECIFIED
 */
class CircuitEditorInitializerSpecTest {

    private val preferences = CircuitEditorPreferencesSnapshot(
        defaultVoltage3Phase = 400,
        defaultMaxVdPower = 5.0,
        defaultConductor = "Cu",
        defaultInsulation = "XLPE",
        defaultCoreConfig = "4C"
    )

    private val transformers = listOf(
        TransformerSnapshot(transformerId = "TR-01", ratingKVA = 1000.0),
        TransformerSnapshot(transformerId = "TR-02", ratingKVA = 1600.0)
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
            ),
            CableType(
                code = 3,
                cableType = "Cu/PVC/PVC - 1C",
                conductor = "Cu",
                insulation = "PVC",
                armour = "None",
                cores = "1C",
                voltage = "0.6/1kV",
                standard = "IEC 60502",
                rColumn = "R_Cu70"
            )
        ),
        cableCatalog = listOf(
            CableCatalogEntry(
                mfg = "Elsewedy",
                typeCode = 1,
                size = 16.0,
                model = "4x16",
                gnd = 90.0,
                duct = 80.0,
                air = 75.0,
                od = 22.0,
                wt = 1200.0
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

    @get:Rule
    val catalogStateReset = CatalogStateResetRule()

    /**
     * Defect VOLT-TESTISO-001: `CircuitEditorInitializer.initialize()` derives the default cable
     * type through the global `resolveCableType()`, which reads the process-wide
     * `Catalog.cableTypes`. Installing this test's own fixture (the same types it passes as
     * `catalogSnapshot`) makes the expectation independent of any catalogue state another test
     * class may have left behind; the rule restores the previous state afterwards.
     */
    @Before
    fun installGlobalCatalogFixture() {
        Catalog.cableTypes = catalog.cableTypes
        Catalog.invalidateIndexes()
    }

    @Test
    fun test01_addMode_initialization() {
        val draft = CircuitEditorInitializer.initialize(
            original = null,
            preferences = preferences,
            availableTransformers = transformers,
            catalogSnapshot = catalog
        )

        assertEquals("", draft.name)
        assertEquals("LOAD", draft.panelType)
        assertEquals("Transformer", draft.fedFrom)
        assertEquals("TR-01", draft.transformerId)
        assertEquals("NORMAL", draft.supplyCategory)
        assertEquals("None", draft.assignedAtsId)
        assertEquals("None", draft.assignedGeneratorId)
        assertEquals("None", draft.assignedUpsId)
        assertEquals("5.0", draft.maxVDpct) // from preferences defaultMaxVdPower
        assertEquals("Elsewedy", draft.manufacturer)
        assertEquals("Copper", draft.filterMat)
        assertEquals("Armoured", draft.filterArmour)
        assertEquals("4C", draft.filterCores)
        assertEquals("0.85", draft.pf) // legacy initial is "0.85"
        assertEquals("1.0", draft.safetyFactor)
        assertEquals("400", draft.voltageV)
    }

    @Test
    fun test02_editMode_entityLoadingAndFractionToPercentConversion() {
        val entity = CircuitEntity(
            id = 10,
            circuitName = "Pump Station",
            loadKVA = 45.0,
            fedFrom = "MDB-Main",
            transformerId = "TR-02",
            generatorIncluded = true,
            atsIncluded = true,
            upsBacked = true,
            assignedGeneratorId = "GEN-01",
            assignedAtsId = "ATS-01",
            assignedUpsId = "UPS-01",
            maxVDpct = 0.03, // Stored as 0.03 (3%)
            manufacturer = "Ducab",
            cableType = "Al/XLPE/STA/PVC - 4C",
            busbarRatingA = 800.0,
            panelType = "MDB"
        )

        val draft = CircuitEditorInitializer.initialize(
            original = entity,
            preferences = preferences,
            availableTransformers = transformers,
            catalogSnapshot = catalog
        )

        assertEquals("Pump Station", draft.name)
        assertEquals("MDB", draft.panelType)
        assertEquals("MDB-Main", draft.fedFrom)
        assertEquals("TR-02", draft.transformerId)
        assertEquals("EMERGENCY + UPS", draft.supplyCategory)
        assertEquals("GEN-01", draft.assignedGeneratorId)
        assertEquals("ATS-01", draft.assignedAtsId)
        assertEquals("UPS-01", draft.assignedUpsId)
        assertEquals("3.0", draft.maxVDpct) // Converted to "3.0"
        assertEquals("Ducab", draft.manufacturer)
        assertEquals("Aluminium", draft.filterMat)
        assertEquals("Armoured", draft.filterArmour)
        assertEquals("4C", draft.filterCores)
        assertEquals("800", draft.busbarRatingA) // cleanly formatted without .0
    }

    @Test
    fun test03_editMode_supplyCategoryDerivations() {
        val emgOnly = CircuitEntity(circuitName = "C", generatorIncluded = true, upsBacked = false)
        val draftEmg = CircuitEditorInitializer.initialize(emgOnly, preferences, transformers, catalog)
        assertEquals("EMERGENCY", draftEmg.supplyCategory)

        val upsOnly = CircuitEntity(circuitName = "C", generatorIncluded = false, upsBacked = true)
        val draftUps = CircuitEditorInitializer.initialize(upsOnly, preferences, transformers, catalog)
        assertEquals("UPS", draftUps.supplyCategory)

        val normal = CircuitEntity(circuitName = "C", generatorIncluded = false, upsBacked = false)
        val draftNormal = CircuitEditorInitializer.initialize(normal, preferences, transformers, catalog)
        assertEquals("NORMAL", draftNormal.supplyCategory)
    }
}
