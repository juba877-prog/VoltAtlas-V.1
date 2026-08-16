package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.sld.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ProjectPersistenceIntegrityTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = AppDatabase.getDatabase(context)
        repository = EngineeringRepository(database)
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
    }

    @Test
    fun testV2ExportContainsAll11EntitiesAndSchemaV2() = runBlocking {
        val projectId = repository.createProject("Master Test Project", "Client XYZ")
        repository.updateProjectImbalanceLimit(projectId, 18.5)

        // 1. Transformer
        val tx = TransformerEntity(
            transformerId = "TX-01",
            projectId = projectId,
            transformerName = "Main Substation TX",
            kVA = 1600.0,
            voltage = 400,
            percentZ = 5.75,
            xr = 8.0,
            vectorGroup = "Dyn11",
            z0OverZ1 = 1.0,
            pf = 0.85,
            txType = "Dry Type (Cast Resin)",
            sourceType = "Medium Voltage",
            status = "Active",
            sizeMode = "Auto",
            manualKVA = 1600.0,
            demandFactor = 0.85,
            diversity = 1.1,
            growth = 1.15,
            safety = 1.2
        )
        repository.insertTransformer(tx)

        // 2. Circuits with Hierarchy
        val mainPanel = CircuitEntity(
            circuitName = "MDB-01",
            projectId = projectId,
            loadKVA = 250.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            panelType = "MDB",
            level = 0,
            assignedGeneratorId = "GEN-01",
            assignedAtsId = "ATS-01",
            assignedUpsId = "UPS-01",
            loadCategory = "Essential",
            busbarRatingA = 630.0,
            scRatingKA = 50.0,
            ipRating = "IP54",
            feederType = "BUSWAY",
            buswayMfg = "Schneider",
            buswayModel = "Canalis KT"
        )
        val mainPanelId = database.circuitDao().insertCircuit(mainPanel).toInt()

        val subPanel = CircuitEntity(
            circuitName = "SMDB-01",
            projectId = projectId,
            loadKVA = 80.0,
            fedFrom = "MDB-01",
            transformerId = "TX-01",
            parentId = mainPanelId,
            panelType = "SMDB",
            level = 1,
            assignedPhase = "L1",
            isHarmonic = true,
            upsBacked = true,
            generatorIncluded = true,
            atsIncluded = true,
            assignedGeneratorId = "GEN-01",
            assignedAtsId = "ATS-01",
            assignedUpsId = "UPS-01"
        )
        val subPanelId = database.circuitDao().insertCircuit(subPanel).toInt()

        // 3. Generator
        val gen = GeneratorEntity(
            projectId = projectId,
            id = 1,
            generatorId = "GEN-01",
            generatorName = "Emergency Diesel Gen",
            additionalEssentialLoadKW = 120.0,
            largestMotorKW = 45.0,
            motorStartMethod = "Star-Delta",
            motorPf = 0.82,
            generatorPf = 0.8,
            demandFactor = 0.85,
            diversityFactor = 1.05,
            stepLoadPct = 60.0,
            oversizingFactor = 1.15,
            enableManualOverride = true,
            manualSizeKVA = 500.0
        )
        repository.saveGeneratorSettings(gen)

        // 4. ATS with outputBoardId
        val ats = AtsEntity(
            projectId = projectId,
            id = 1,
            atsId = "ATS-01",
            atsName = "Automatic Transfer Switch 1",
            systemVoltage = 400,
            systemFrequency = 50,
            numberOfSources = 2,
            atsLocation = "Main Intake",
            transferType = "Closed Transition",
            utilizationCategory = "AC-33A",
            atsClass = "PC",
            loadCoverage = "Total Facility",
            ambientTemp = 45,
            designSafetyFactor = 1.25,
            futureGrowthFactor = 15.0,
            approvedManufacturer = "ABB",
            mainsFailureDetectionDelay = 3,
            generatorStartSignalDelay = 1,
            generatorWarmUp = 10,
            mainsReturnDetectionDelay = 60,
            reTransferDelay = 15,
            generatorCooldown = 300,
            enclosureIpRating = "IP54",
            normalSourceId = "TX-01",
            emergencySourceId = "GEN-01",
            outputBoardId = mainPanelId,
            enableManualOverride = true,
            manualSizeAmp = 630.0
        )
        repository.saveAtsSettings(ats)

        // 5. UPS
        val ups = UpsEntity(
            projectId = projectId,
            id = 1,
            upsId = "UPS-01",
            upsName = "Data Center UPS",
            systemVoltage = 400,
            systemFrequency = 50,
            upsPf = 0.95,
            upsEfficiency = 96.5,
            batteryType = "Li-Ion",
            autonomyMinutes = 30,
            batteryEodVoltage = 1.70,
            ambientTemp = 25,
            tempDeratingFactor = 1.0,
            agingFactor = 1.2,
            designMarginPct = 15.0,
            enableManualOverride = true,
            manualSizeKVA = 160.0
        )
        repository.saveUpsSettings(ups)

        // 6. PF Global
        val pfGlobal = PfGlobalEntity(
            projectId = projectId,
            id = 1,
            targetPf = 0.98,
            kvarStepSize = 50.0,
            systemVoltage = 400,
            annualOperatingHours = 8760,
            energyCost = 0.15,
            lossReductionPct = 18.0,
            unityToleranceKvar = 10.0
        )
        repository.savePfGlobalSettings(pfGlobal)

        // 7. PF Transformer
        val pfTx = PfTransformerEntity(
            projectId = projectId,
            transformerId = "TX-01",
            harmonicFilter = "Active",
            detuningReactor = "7%"
        )
        repository.savePfTransformerSettings(pfTx)

        // 8. Conduit Settings
        val conduit = ConduitSettingsEntity(
            projectId = projectId,
            circuitId = mainPanelId,
            routingMode = "ONE_PER_CONDUIT",
            designProfile = "SPEC_HIGH_TEMP",
            customFillLimitPct = 38.0,
            isDirectBuried = false,
            cpcArrangement = "SEPARATE_CORE",
            manualConduitOverride = "50mm PVC",
            manualCpcOD = 12.5
        )
        repository.saveConduitSetting(conduit)

        // 9. Earthing Settings
        val earthing = EarthingEntity(
            projectId = projectId,
            id = 1,
            soilResistivity_ohmM = 85.0,
            rodLength_m = 4.0,
            rodDiameter_mm = 19.0,
            rodSpacing_m = 8.0,
            ringConductor_mm2 = 95.0,
            ringBurialDepth_m = 0.8,
            kFactor = 143.0,
            projectType = "Industrial",
            disconnectionTime_s = 0.4,
            targetResistanceOverride_ohm = 1.0,
            soilResistivitySource = "Four-Point Wenner Survey",
            designMarginPct = 20.0,
            isAutoDesignEnabled = true,
            siteLength_m = 50.0,
            siteWidth_m = 30.0,
            autoDesignAppliedConfigId = "GRID_ROD_COMBINED"
        )
        repository.saveEarthingSettings(earthing)

        // 10. SLD Node Positions
        database.sldNodePositionDao().insertOrUpdatePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "circ_$mainPanelId", x = 120f, y = 240f)
        )
        database.sldNodePositionDao().insertOrUpdatePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "tx_TX-01", x = 120f, y = 50f)
        )

        // Export V2 JSON
        val exportedJson = repository.exportProjectJson(projectId)
        assertNotNull(exportedJson)
        val root = JSONObject(exportedJson)

        assertEquals("VoltAtlasProject", root.getString("schema"))
        assertEquals(2, root.getInt("version"))
        assertEquals("VoltAtlas", root.getString("appName"))
        assertTrue(root.has("exportedAt"))
        assertTrue(root.has("project"))
        assertTrue(root.has("transformers"))
        assertTrue(root.has("circuits"))
        assertTrue(root.has("generators"))
        assertTrue(root.has("ats"))
        assertTrue(root.has("ups"))
        assertTrue(root.has("pfGlobal"))
        assertTrue(root.has("pfTransformers"))
        assertTrue(root.has("conduitSettings"))
        assertTrue(root.has("earthingSettings"))
        assertTrue(root.has("sldNodePositions"))

        assertEquals(1, root.getJSONArray("transformers").length())
        assertEquals(2, root.getJSONArray("circuits").length())
        assertEquals(1, root.getJSONArray("generators").length())
        assertEquals(1, root.getJSONArray("ats").length())
        assertEquals(1, root.getJSONArray("ups").length())
        assertEquals(1, root.getJSONArray("pfTransformers").length())
        assertEquals(1, root.getJSONArray("conduitSettings").length())
        assertEquals(2, root.getJSONArray("sldNodePositions").length())
    }

    @Test
    fun testV2RoundTripFieldByFieldIntegrityAndRemapping() = runBlocking {
        val projectId = repository.createProject("Comprehensive RT Project", "Client Universal")
        repository.updateProjectImbalanceLimit(projectId, 16.0)

        // 1. Transformer - Comprehensive field coverage
        val originalTx = TransformerEntity(
            transformerId = "TX-01",
            projectId = projectId,
            transformerName = "TX-Substation-1",
            kVA = 1250.0,
            voltage = 415,
            percentZ = 6.0,
            xr = 7.5,
            txType = "Oil Immersed",
            sourceType = "High Voltage",
            status = "Active",
            sizeMode = "Auto",
            manualKVA = 1250.0,
            demandFactor = 0.9,
            diversity = 1.05,
            growth = 1.1,
            safety = 1.15,
            vectorGroup = "Dyn11",
            z0OverZ1 = 0.95,
            pf = 0.88
        )
        repository.insertTransformer(originalTx)

        // 2. Circuits with Hierarchy and References - Comprehensive field coverage
        val originalParent = CircuitEntity(
            circuitName = "Main Incomer MDB",
            projectId = projectId,
            loadKVA = 350.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            panelType = "MDB",
            level = 0,
            phases = "3P+N",
            voltageV = 415,
            pf = 0.88,
            safetyFactor = 1.25,
            manufacturer = "Elsewedy Electric",
            cableType = "CU/XLPE/PVC",
            installation = "C",
            airTempC = 45,
            gndTempC = 35,
            burialDepth_m = 0.8,
            soilRes = 150,
            noCircuits = 2,
            formation = "Trefoil",
            spacing = "Spaced",
            parallelRuns = 2,
            length_m = 40.0,
            maxVDpct = 0.03,
            overrideDeviceType = "ACB",
            overrideDeviceSize = 800.0,
            overrideCableSize = "2x(3x240+120)",
            overrideNeutralSize = "240",
            overrideEarthSize = "120",
            isHarmonic = false,
            assignedPhase = null,
            upsBacked = false,
            demandFactor = 0.9,
            diversityGroup = "HVAC",
            generatorIncluded = true,
            atsIncluded = true,
            assignedGeneratorId = "GEN-MAIN",
            assignedAtsId = "ATS-MAIN",
            assignedUpsId = "UPS-CRITICAL",
            loadNature = "Mixed",
            startingMethod = "Direct",
            startingCurrentMultiplier = 1.0,
            active = true,
            sourceType = "Mains",
            directLoadConfirmed = true,
            phaseImbalanceLimitOverridePct = 15.0,
            loadCategory = "Essential",
            busbarRatingA = 800.0,
            scRatingKA = 65.0,
            mainIncomingType = "ACB 4P",
            mainIncomingCount = 1,
            ipRating = "IP55",
            panelLocation = "Substation 1",
            cableEntry = "Bottom",
            feederDescription = "Main Plant Incomer",
            numberOfWays = 12,
            feederType = "CABLE",
            buswayMfg = "Schneider",
            buswayModel = "Canalis KT"
        )
        val origParentId = database.circuitDao().insertCircuit(originalParent).toInt()

        val originalChild = CircuitEntity(
            circuitName = "Sub-Distribution SMDB-1",
            projectId = projectId,
            loadKVA = 110.0,
            fedFrom = "Main Incomer MDB",
            transformerId = "TX-01",
            parentId = origParentId,
            panelType = "SMDB",
            level = 1,
            phases = "3P+N",
            voltageV = 415,
            pf = 0.85,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 25.0,
            maxVDpct = 0.02,
            overrideDeviceType = "MCCB",
            overrideDeviceSize = 250.0,
            overrideCableSize = "3x70+35",
            overrideNeutralSize = "35",
            overrideEarthSize = "35",
            isHarmonic = true,
            assignedPhase = "L1",
            upsBacked = true,
            demandFactor = 0.85,
            diversityGroup = "Lighting",
            generatorIncluded = true,
            atsIncluded = true,
            assignedGeneratorId = "GEN-MAIN",
            assignedAtsId = "ATS-MAIN",
            assignedUpsId = "UPS-CRITICAL",
            loadNature = "Static",
            startingMethod = null,
            startingCurrentMultiplier = null,
            active = true,
            sourceType = "Panel",
            directLoadConfirmed = false,
            phaseImbalanceLimitOverridePct = 12.0,
            loadCategory = "Normal",
            busbarRatingA = 250.0,
            scRatingKA = 36.0,
            mainIncomingType = "MCCB 3P",
            mainIncomingCount = 1,
            ipRating = "IP43",
            panelLocation = "Floor 1 Electrical Room",
            cableEntry = "Top",
            feederDescription = "Lighting Feeder",
            numberOfWays = 18,
            feederType = "Cable",
            buswayMfg = null,
            buswayModel = null
        )
        val origChildId = database.circuitDao().insertCircuit(originalChild).toInt()

        // 3. Generator - Full fields
        val originalGen = GeneratorEntity(
            projectId = projectId,
            id = 1,
            generatorId = "GEN-MAIN",
            generatorName = "Standby Diesel Gen 1",
            additionalEssentialLoadKW = 150.0,
            largestMotorKW = 55.0,
            motorStartMethod = "Soft Starter",
            motorPf = 0.85,
            generatorPf = 0.8,
            demandFactor = 0.85,
            diversityFactor = 1.0,
            stepLoadPct = 50.0,
            oversizingFactor = 1.2,
            enableManualOverride = true,
            manualSizeKVA = 750.0
        )
        repository.saveGeneratorSettings(originalGen)

        // 4. ATS with outputBoardId - Full fields
        val originalAts = AtsEntity(
            projectId = projectId,
            id = 1,
            atsId = "ATS-MAIN",
            atsName = "Automatic Transfer Switch Main",
            systemVoltage = 415,
            systemFrequency = 50,
            numberOfSources = 2,
            atsLocation = "Main Switchroom",
            transferType = "Open Transition",
            utilizationCategory = "AC-31A",
            atsClass = "PC",
            loadCoverage = "Emergency Only",
            ambientTemp = 40,
            designSafetyFactor = 1.25,
            futureGrowthFactor = 10.0,
            approvedManufacturer = "Schneider Electric",
            mainsFailureDetectionDelay = 3,
            generatorStartSignalDelay = 0,
            generatorWarmUp = 7,
            mainsReturnDetectionDelay = 60,
            reTransferDelay = 10,
            generatorCooldown = 300,
            enclosureIpRating = "IP54",
            normalSourceId = "TX-01",
            emergencySourceId = "GEN-MAIN",
            outputBoardId = origParentId,
            enableManualOverride = true,
            manualSizeAmp = 800.0
        )
        repository.saveAtsSettings(originalAts)

        // 5. UPS - Full fields
        val originalUps = UpsEntity(
            projectId = projectId,
            id = 1,
            upsId = "UPS-CRITICAL",
            upsName = "Critical Load UPS",
            systemVoltage = 415,
            systemFrequency = 50,
            upsPf = 0.9,
            upsEfficiency = 95.0,
            batteryType = "VRLA",
            autonomyMinutes = 20,
            batteryEodVoltage = 1.75,
            ambientTemp = 25,
            tempDeratingFactor = 1.0,
            agingFactor = 1.25,
            designMarginPct = 10.0,
            enableManualOverride = true,
            manualSizeKVA = 200.0
        )
        repository.saveUpsSettings(originalUps)

        // 6. PF Global - Full fields
        val originalPfGlobal = PfGlobalEntity(
            projectId = projectId,
            id = 1,
            targetPf = 0.97,
            kvarStepSize = 25.0,
            systemVoltage = 415,
            annualOperatingHours = 8000,
            energyCost = 0.14,
            lossReductionPct = 12.0,
            unityToleranceKvar = 5.0
        )
        repository.savePfGlobalSettings(originalPfGlobal)

        // 7. PF Transformer - Full fields
        val originalPfTx = PfTransformerEntity(
            projectId = projectId,
            transformerId = "TX-01",
            harmonicFilter = "Passive",
            detuningReactor = "5.67%"
        )
        repository.savePfTransformerSettings(originalPfTx)

        // 8. Conduit Settings - Full fields
        val originalConduit = ConduitSettingsEntity(
            projectId = projectId,
            circuitId = origParentId,
            routingMode = "ONE_PER_CONDUIT",
            designProfile = "PROJECT_DESIGN",
            customFillLimitPct = 40.0,
            isDirectBuried = false,
            cpcArrangement = "INTERNAL_CORE",
            manualConduitOverride = "63mm HDPE",
            manualCpcOD = 14.0
        )
        repository.saveConduitSetting(originalConduit)

        // 9. Earthing Settings - Full fields
        val originalEarthing = EarthingEntity(
            projectId = projectId,
            id = 1,
            soilResistivity_ohmM = 90.0,
            rodLength_m = 3.0,
            rodDiameter_mm = 16.0,
            rodSpacing_m = 6.0,
            ringConductor_mm2 = 70.0,
            ringBurialDepth_m = 0.6,
            kFactor = 143.0,
            projectType = "Commercial",
            disconnectionTime_s = 0.2,
            targetResistanceOverride_ohm = 2.0,
            soilResistivitySource = "Soil Analysis",
            designMarginPct = 15.0,
            isAutoDesignEnabled = true,
            siteLength_m = 40.0,
            siteWidth_m = 25.0,
            autoDesignAppliedConfigId = "GRID_ROD_COMBINED"
        )
        repository.saveEarthingSettings(originalEarthing)

        // 10. SLD Node Positions - Full fields
        database.sldNodePositionDao().insertOrUpdatePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "circ_$origParentId", x = 150f, y = 200f)
        )
        database.sldNodePositionDao().insertOrUpdatePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "tx_TX-01", x = 150f, y = 50f)
        )

        // Export V2 JSON
        val exportedJson = repository.exportProjectJson(projectId)
        assertNotNull(exportedJson)

        // Import into clean database space
        val result = repository.importProjectFromJsonDetailed(exportedJson)
        assertEquals(ImportStatus.SUCCESS, result.status)
        assertTrue(result.errors.isEmpty())
        assertNotNull(result.projectId)
        val newProjId = result.projectId!!
        assertNotEquals(projectId, newProjId)

        // --- 1. Validate Complete ProjectEntity Snapshot ---
        val newProj = database.projectDao().getProject(newProjId)
        assertNotNull(newProj)
        assertEquals(newProjId, newProj!!.projectId)
        assertEquals("Comprehensive RT Project", newProj.projectName)
        assertEquals("Client Universal", newProj.clientName)
        assertEquals(16.0, newProj.defaultPhaseImbalanceLimitPct, 0.001)

        // --- 2. Validate Complete TransformerEntity Snapshot ---
        val newTxList = database.transformerDao().getTransformersListForProject(newProjId)
        assertEquals(1, newTxList.size)
        val newTx = newTxList.first()
        // Compare with originalTx snapshot (ignoring only projectId which is remapped)
        assertEquals(originalTx.copy(projectId = newProjId), newTx)

        // --- 3. Validate Complete CircuitEntity Snapshots & Hierarchy ---
        val newCircuits = database.circuitDao().getCircuitsListForProject(newProjId)
        assertEquals(2, newCircuits.size)
        val newParent = newCircuits.first { it.circuitName == "Main Incomer MDB" }
        val newChild = newCircuits.first { it.circuitName == "Sub-Distribution SMDB-1" }

        // Generated IDs must be new
        assertNotEquals(origParentId, newParent.id)
        assertNotEquals(origChildId, newChild.id)

        // Compare complete parent circuit snapshot (ignoring auto-generated id and remapped projectId)
        val expectedParent = originalParent.copy(id = newParent.id, projectId = newProjId)
        assertEquals(expectedParent, newParent)

        // Compare complete child circuit snapshot (ignoring auto-generated id, remapped projectId, and remapped parentId)
        val expectedChild = originalChild.copy(id = newChild.id, projectId = newProjId, parentId = newParent.id)
        assertEquals(expectedChild, newChild)

        // --- 4. Validate Complete GeneratorEntity Snapshot ---
        val newGenList = database.powerSystemsDao().getAllGeneratorsList(newProjId)
        assertEquals(1, newGenList.size)
        val newGen = newGenList.first()
        val expectedGen = originalGen.copy(projectId = newProjId)
        assertEquals(expectedGen, newGen)

        // --- 5. Validate Complete AtsEntity Snapshot & outputBoardId Remapping ---
        val newAtsList = database.powerSystemsDao().getAllAtsList(newProjId)
        assertEquals(1, newAtsList.size)
        val newAts = newAtsList.first()
        val expectedAts = originalAts.copy(projectId = newProjId, outputBoardId = newParent.id)
        assertEquals(expectedAts, newAts)

        // --- 6. Validate Complete UpsEntity Snapshot ---
        val newUpsList = database.powerSystemsDao().getAllUpsList(newProjId)
        assertEquals(1, newUpsList.size)
        val newUps = newUpsList.first()
        val expectedUps = originalUps.copy(projectId = newProjId)
        assertEquals(expectedUps, newUps)

        // --- 7. Validate Complete PfGlobalEntity Snapshot ---
        val newPfGlobal = database.powerSystemsDao().getPfGlobalSettingsDirect(newProjId)
        assertNotNull(newPfGlobal)
        val expectedPfGlobal = originalPfGlobal.copy(projectId = newProjId)
        assertEquals(expectedPfGlobal, newPfGlobal)

        // --- 8. Validate Complete PfTransformerEntity Snapshot ---
        val newPfTxList = database.powerSystemsDao().getAllPfTransformerSettingsList(newProjId)
        assertEquals(1, newPfTxList.size)
        val expectedPfTx = originalPfTx.copy(projectId = newProjId)
        assertEquals(expectedPfTx, newPfTxList.first())

        // --- 9. Validate Complete ConduitSettingsEntity Snapshot & circuitId Remapping ---
        val newConduitList = database.conduitDao().getConduitSettingsListForProject(newProjId)
        assertEquals(1, newConduitList.size)
        val newConduit = newConduitList.first()
        val expectedConduit = originalConduit.copy(projectId = newProjId, circuitId = newParent.id)
        assertEquals(expectedConduit, newConduit)

        // --- 10. Validate Complete EarthingEntity Snapshot ---
        val newEarthing = database.powerSystemsDao().getEarthingSettingsDirect(newProjId)
        assertNotNull(newEarthing)
        val expectedEarthing = originalEarthing.copy(projectId = newProjId)
        assertEquals(expectedEarthing, newEarthing)

        // --- 11. Validate Complete SldNodePositionEntity Snapshots & circ_<oldId> → circ_<newId> Remapping ---
        val newSldList = database.sldNodePositionDao().getPositionsForProjectSync(newProjId)
        assertEquals(2, newSldList.size)

        val remappedCircNode = newSldList.firstOrNull { it.nodeId == "circ_${newParent.id}" }
        assertNotNull("Expected node circ_${newParent.id} in SLD positions", remappedCircNode)
        assertEquals(SldNodePositionEntity(projectId = newProjId, nodeId = "circ_${newParent.id}", x = 150f, y = 200f), remappedCircNode)

        val txNode = newSldList.firstOrNull { it.nodeId == "tx_TX-01" }
        assertNotNull(txNode)
        assertEquals(SldNodePositionEntity(projectId = newProjId, nodeId = "tx_TX-01", x = 150f, y = 50f), txNode)
    }

    @Test
    fun testV1MigrationExactMatchResolvesParentId() = runBlocking {
        // Scenario A: Exactly one matching circuit name in project -> parentId resolved + MIGRATED notice
        val v1Json = """
        {
          "version": 1,
          "appName": "VoltAtlas",
          "project": { "projectName": "V1 Exact Match Project" },
          "circuits": [
            {
              "id": 10,
              "circuitName": "MDB-Main",
              "fedFrom": "Transformer",
              "loadKVA": 100.0
            },
            {
              "id": 11,
              "circuitName": "DB-Lighting",
              "fedFrom": "MDB-Main",
              "loadKVA": 20.0
            }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(v1Json)
        assertEquals(1, result.schemaVersion)
        assertEquals(ImportStatus.SUCCESS, result.status)
        assertNotNull(result.projectId)
        val newProjId = result.projectId!!

        val circuits = database.circuitDao().getCircuitsListForProject(newProjId)
        assertEquals(2, circuits.size)
        val parent = circuits.first { it.circuitName == "MDB-Main" }
        val child = circuits.first { it.circuitName == "DB-Lighting" }

        // Resolved to unique parent ID
        assertEquals(parent.id, child.parentId)

        val migrationNotice = result.notices.find { it.entityIdentifier == "DB-Lighting" && it.fieldName == "parentId" }
        assertNotNull(migrationNotice)
        assertEquals(MigrationFieldClassification.MIGRATED, migrationNotice!!.classification)
    }

    @Test
    fun testV1MigrationZeroMatchesLeavesParentIdNullWithWarning() = runBlocking {
        // Scenario B: Zero matching circuits for fedFrom -> parentId = null + UNAVAILABLE_FROM_V1 notice + warning
        val v1Json = """
        {
          "version": 1,
          "appName": "VoltAtlas",
          "project": { "projectName": "V1 Zero Match Project" },
          "circuits": [
            {
              "id": 1,
              "circuitName": "Orphan Sub-Panel",
              "fedFrom": "UnknownNonExistentPanel",
              "loadKVA": 15.0
            }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(v1Json)
        assertEquals(1, result.schemaVersion)
        assertEquals(ImportStatus.SUCCESS_WITH_WARNINGS, result.status)
        assertNotNull(result.projectId)
        val newProjId = result.projectId!!

        val circuits = database.circuitDao().getCircuitsListForProject(newProjId)
        assertEquals(1, circuits.size)
        val circuit = circuits.first()

        // parentId remains unassigned
        assertNull(circuit.parentId)

        // Notice and warning recorded
        val notice = result.notices.find { it.entityIdentifier == "Orphan Sub-Panel" && it.fieldName == "parentId" }
        assertNotNull(notice)
        assertEquals(MigrationFieldClassification.UNAVAILABLE_FROM_V1, notice!!.classification)
        assertTrue(result.warnings.any { it.contains("could not be resolved") })
    }

    @Test
    fun testV1MigrationMultipleMatchesLeavesParentIdNullWithWarning() = runBlocking {
        // Scenario C: Multiple matching circuits with identical names -> parentId = null + INVALID notice + warning
        val v1Json = """
        {
          "version": 1,
          "appName": "VoltAtlas",
          "project": { "projectName": "V1 Ambiguous Match Project" },
          "circuits": [
            {
              "id": 1,
              "circuitName": "Repeated Name Panel",
              "fedFrom": "Transformer",
              "loadKVA": 50.0
            },
            {
              "id": 2,
              "circuitName": "Repeated Name Panel",
              "fedFrom": "Transformer",
              "loadKVA": 50.0
            },
            {
              "id": 3,
              "circuitName": "Sub Child Panel",
              "fedFrom": "Repeated Name Panel",
              "loadKVA": 10.0
            }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(v1Json)
        assertEquals(1, result.schemaVersion)
        assertEquals(ImportStatus.SUCCESS_WITH_WARNINGS, result.status)
        assertNotNull(result.projectId)
        val newProjId = result.projectId!!

        val circuits = database.circuitDao().getCircuitsListForProject(newProjId)
        assertEquals(3, circuits.size)
        val child = circuits.first { it.circuitName == "Sub Child Panel" }

        // Ambiguous match must NOT guess, stays null safely
        assertNull(child.parentId)

        val notice = result.notices.find { it.entityIdentifier == "Sub Child Panel" && it.fieldName == "parentId" }
        assertNotNull(notice)
        assertEquals(MigrationFieldClassification.INVALID, notice!!.classification)
        assertTrue(result.warnings.any { it.contains("ambiguous") })
    }

    @Test
    fun testAtomicProjectDeletionPurgesAll11Tables() = runBlocking {
        val projectId = repository.createProject("Project to Delete", "Client 123")
        
        // Populate all tables
        repository.insertTransformer(TransformerEntity(transformerId = "TX-01", projectId = projectId))
        val circId = database.circuitDao().insertCircuit(CircuitEntity(circuitName = "C1", projectId = projectId, transformerId = "TX-01")).toInt()
        repository.saveGeneratorSettings(GeneratorEntity(projectId = projectId, id = 1, generatorId = "GEN-01"))
        repository.saveAtsSettings(AtsEntity(projectId = projectId, id = 1, atsId = "ATS-01"))
        repository.saveUpsSettings(UpsEntity(projectId = projectId, id = 1, upsId = "UPS-01"))
        repository.savePfGlobalSettings(PfGlobalEntity(projectId = projectId, id = 1))
        repository.savePfTransformerSettings(PfTransformerEntity(projectId = projectId, transformerId = "TX-01"))
        repository.saveConduitSetting(ConduitSettingsEntity(projectId = projectId, circuitId = circId))
        repository.saveEarthingSettings(EarthingEntity(projectId = projectId, id = 1))
        database.sldNodePositionDao().insertOrUpdatePosition(SldNodePositionEntity(projectId = projectId, nodeId = "tx_TX-01", x = 10f, y = 20f))

        // Ensure records exist
        assertEquals(1, database.transformerDao().getTransformersListForProject(projectId).size)
        assertEquals(1, database.circuitDao().getCircuitsListForProject(projectId).size)
        assertEquals(1, database.powerSystemsDao().getAllGeneratorsList(projectId).size)
        assertEquals(1, database.powerSystemsDao().getAllAtsList(projectId).size)
        assertEquals(1, database.powerSystemsDao().getAllUpsList(projectId).size)
        assertNotNull(database.powerSystemsDao().getPfGlobalSettingsDirect(projectId))
        assertEquals(1, database.powerSystemsDao().getAllPfTransformerSettingsList(projectId).size)
        assertEquals(1, database.conduitDao().getConduitSettingsListForProject(projectId).size)
        assertNotNull(database.powerSystemsDao().getEarthingSettingsDirect(projectId))
        assertEquals(1, database.sldNodePositionDao().getPositionsForProjectSync(projectId).size)

        // Atomic Delete
        repository.deleteProject(projectId)

        // Verify all 11 tables are completely empty for this projectId
        assertNull(database.projectDao().getProject(projectId))
        assertTrue(database.transformerDao().getTransformersListForProject(projectId).isEmpty())
        assertTrue(database.circuitDao().getCircuitsListForProject(projectId).isEmpty())
        assertTrue(database.powerSystemsDao().getAllGeneratorsList(projectId).isEmpty())
        assertTrue(database.powerSystemsDao().getAllAtsList(projectId).isEmpty())
        assertTrue(database.powerSystemsDao().getAllUpsList(projectId).isEmpty())
        assertNull(database.powerSystemsDao().getPfGlobalSettingsDirect(projectId))
        assertTrue(database.powerSystemsDao().getAllPfTransformerSettingsList(projectId).isEmpty())
        assertTrue(database.conduitDao().getConduitSettingsListForProject(projectId).isEmpty())
        assertNull(database.powerSystemsDao().getEarthingSettingsDirect(projectId))
        assertTrue(database.sldNodePositionDao().getPositionsForProjectSync(projectId).isEmpty())
    }

    @Test
    fun testV2UnresolvedHierarchyRejectionRollback() = runBlocking {
        val initialProjectsCount = repository.getAllProjectsList().size

        // Malformed V2 with unresolved parentId (parentId = 99999 does not exist in circuits)
        val badV2Json = """
        {
          "schema": "VoltAtlasProject",
          "version": 2,
          "appName": "VoltAtlas",
          "project": {
            "projectName": "Invalid Hierarchy Project"
          },
          "transformers": [
            { "transformerId": "TX-01", "transformerName": "TX 1" }
          ],
          "circuits": [
            {
              "id": 1,
              "circuitName": "Orphan Child",
              "loadKVA": 10.0,
              "fedFrom": "NonExistentParent",
              "transformerId": "TX-01",
              "parentId": 99999
            }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(badV2Json)
        assertEquals(ImportStatus.FAILED, result.status)
        assertNull(result.projectId)
        assertTrue(result.errors.isNotEmpty())

        // Verify atomic rollback: no orphan project or circuits created in database
        val afterProjectsCount = repository.getAllProjectsList().size
        assertEquals(initialProjectsCount, afterProjectsCount)
    }

    @Test
    fun testV2UnresolvedAtsOutputBoardRejectionRollback() = runBlocking {
        val initialProjectsCount = repository.getAllProjectsList().size

        val badAtsJson = """
        {
          "schema": "VoltAtlasProject",
          "version": 2,
          "appName": "VoltAtlas",
          "project": { "projectName": "Bad ATS Project" },
          "circuits": [
            { "id": 1, "circuitName": "Panel 1", "fedFrom": "Transformer", "loadKVA": 10.0 }
          ],
          "ats": [
            { "id": 1, "atsId": "ATS-01", "atsName": "ATS 1", "outputBoardId": 8888 }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(badAtsJson)
        assertEquals(ImportStatus.FAILED, result.status)
        assertNull(result.projectId)
        assertTrue(result.errors.isNotEmpty())

        val afterProjectsCount = repository.getAllProjectsList().size
        assertEquals(initialProjectsCount, afterProjectsCount)
    }

    @Test
    fun testV2UnresolvedConduitCircuitRejectionRollback() = runBlocking {
        val initialProjectsCount = repository.getAllProjectsList().size

        val badConduitJson = """
        {
          "schema": "VoltAtlasProject",
          "version": 2,
          "appName": "VoltAtlas",
          "project": { "projectName": "Bad Conduit Project" },
          "circuits": [
            { "id": 1, "circuitName": "Panel 1", "fedFrom": "Transformer", "loadKVA": 10.0 }
          ],
          "conduitSettings": [
            { "circuitId": 7777, "routingMode": "ONE_PER_CONDUIT" }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(badConduitJson)
        assertEquals(ImportStatus.FAILED, result.status)
        assertNull(result.projectId)
        assertTrue(result.errors.isNotEmpty())

        val afterProjectsCount = repository.getAllProjectsList().size
        assertEquals(initialProjectsCount, afterProjectsCount)
    }

    @Test
    fun testV2UnresolvedSldCircuitNodeRejectionRollback() = runBlocking {
        val initialProjectsCount = repository.getAllProjectsList().size

        val badSldJson = """
        {
          "schema": "VoltAtlasProject",
          "version": 2,
          "appName": "VoltAtlas",
          "project": { "projectName": "Bad SLD Project" },
          "circuits": [
            { "id": 1, "circuitName": "Panel 1", "fedFrom": "Transformer", "loadKVA": 10.0 }
          ],
          "sldNodePositions": [
            { "nodeId": "circ_6666", "x": 50.0, "y": 50.0 }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(badSldJson)
        assertEquals(ImportStatus.FAILED, result.status)
        assertNull(result.projectId)
        assertTrue(result.errors.isNotEmpty())

        val afterProjectsCount = repository.getAllProjectsList().size
        assertEquals(initialProjectsCount, afterProjectsCount)
    }

    @Test
    fun testSldGraphIntegrityAfterImport() = runBlocking {
        // Complete project with multi-source hierarchy (Grid -> TX -> MDB -> SMDB, GEN -> ATS -> MDB)
        val v2Json = """
        {
          "schema": "VoltAtlasProject",
          "version": 2,
          "appName": "VoltAtlas",
          "project": { "projectName": "SLD Verification Project" },
          "transformers": [
            { "transformerId": "TX-01", "transformerName": "Substation TX 1", "kVA": 1000.0 }
          ],
          "generators": [
            { "id": 1, "generatorId": "GEN-01", "generatorName": "Standby Diesel Gen", "manualSizeKVA": 500.0 }
          ],
          "circuits": [
            {
              "id": 101,
              "circuitName": "MDB-Main",
              "fedFrom": "Transformer",
              "transformerId": "TX-01",
              "panelType": "MDB",
              "loadKVA": 300.0
            },
            {
              "id": 102,
              "circuitName": "SMDB-HVAC",
              "fedFrom": "MDB-Main",
              "transformerId": "TX-01",
              "parentId": 101,
              "panelType": "SMDB",
              "loadKVA": 100.0
            },
            {
              "id": 103,
              "circuitName": "DB-Lighting",
              "fedFrom": "SMDB-HVAC",
              "transformerId": "TX-01",
              "parentId": 102,
              "panelType": "DB",
              "loadKVA": 30.0
            }
          ],
          "ats": [
            {
              "id": 1,
              "atsId": "ATS-01",
              "atsName": "Main ATS",
              "normalSourceId": "TX-01",
              "emergencySourceId": "GEN-01",
              "outputBoardId": 101
            }
          ]
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(v2Json)
        assertEquals(ImportStatus.SUCCESS, result.status)
        val projId = result.projectId!!

        val dbTransformers = database.transformerDao().getTransformersListForProject(projId)
        val dbCircuits = database.circuitDao().getCircuitsListForProject(projId)
        val dbGenerators = database.powerSystemsDao().getAllGeneratorsList(projId)
        val dbAts = database.powerSystemsDao().getAllAtsList(projId)

        assertEquals(1, dbTransformers.size)
        assertEquals(3, dbCircuits.size)
        assertEquals(1, dbGenerators.size)
        assertEquals(1, dbAts.size)

        val mdb = dbCircuits.first { it.circuitName == "MDB-Main" }
        val smdb = dbCircuits.first { it.circuitName == "SMDB-HVAC" }
        val dbLight = dbCircuits.first { it.circuitName == "DB-Lighting" }

        val parentsMap = mapOf(
            smdb.id to mdb.id,
            dbLight.id to smdb.id
        )

        // Build SLD Graph
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projId,
            transformers = dbTransformers,
            circuits = dbCircuits,
            circuitParents = parentsMap,
            generators = dbGenerators,
            atsList = dbAts
        )

        // 1. Node Count Verification:
        // Expected nodes: grid_TX-01, tx_TX-01, gen_GEN-01, ats_ATS-01, circ_mdb, circ_smdb, circ_db
        assertEquals(7, graph.nodes.size)
        assertEquals(0, graph.unconnectedNodes.size)

        val nodeIds = graph.nodes.map { it.id }.toSet()
        assertTrue(nodeIds.contains("grid_TX-01"))
        assertTrue(nodeIds.contains("tx_TX-01"))
        assertTrue(nodeIds.contains("gen_GEN-01"))
        assertTrue(nodeIds.contains("ats_ATS-01"))
        assertTrue(nodeIds.contains("circ_${mdb.id}"))
        assertTrue(nodeIds.contains("circ_${smdb.id}"))
        assertTrue(nodeIds.contains("circ_${dbLight.id}"))

        // 2. Edge Count & Source/Destination Relationships:
        // Expected edges:
        // - Grid -> TX
        // - TX -> ATS (normal source)
        // - GEN -> ATS (emergency source)
        // - ATS -> MDB (output board)
        // - MDB -> SMDB (hierarchy)
        // - SMDB -> DB (hierarchy)
        assertEquals(6, graph.edges.size)

        assertTrue("Grid -> TX edge", graph.edges.any { it.fromNodeId == "grid_TX-01" && it.toNodeId == "tx_TX-01" })
        assertTrue("TX -> ATS edge", graph.edges.any { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-01" })
        assertTrue("GEN -> ATS edge", graph.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" })
        assertTrue("ATS -> MDB edge", graph.edges.any { it.fromNodeId == "ats_ATS-01" && it.toNodeId == "circ_${mdb.id}" })
        assertTrue("MDB -> SMDB edge", graph.edges.any { it.fromNodeId == "circ_${mdb.id}" && it.toNodeId == "circ_${smdb.id}" })
        assertTrue("SMDB -> DB edge", graph.edges.any { it.fromNodeId == "circ_${smdb.id}" && it.toNodeId == "circ_${dbLight.id}" })

        // 3. Validation Issues:
        // No missing references or orphaned nodes
        assertEquals(0, graph.validationIssues.size)
    }

    @Test
    fun testParentCircuitDeletionCascadeCleanupPreventsOrphanedParentId() = runBlocking {
        val projectId = repository.createProject("Cascade Test Project", "Client ABC")

        // 1. Create parent distribution board (e.g. DP-G3)
        val parent = CircuitEntity(
            circuitName = "DP-G3",
            projectId = projectId,
            loadKVA = 87.9,
            fedFrom = "Transformer",
            panelType = "MDB",
            sourceType = "TRANSFORMER",
            parentId = null,
            level = 0
        )
        val parentId = repository.insertCircuit(parent).toInt()
        val parentWithId = parent.copy(id = parentId)

        // 2. Create multiple child circuits that reference parentId = parentId
        val child1 = CircuitEntity(
            circuitName = "UPP-G3-1",
            projectId = projectId,
            loadKVA = 6.4,
            fedFrom = "DP-G3",
            panelType = "DB",
            sourceType = "PANEL",
            parentId = parentId,
            level = 1
        )
        val child2 = CircuitEntity(
            circuitName = "UPP-G3-2",
            projectId = projectId,
            loadKVA = 5.0,
            fedFrom = "DP-G3",
            panelType = "DB",
            sourceType = "PANEL",
            parentId = parentId,
            level = 1
        )
        val child3 = CircuitEntity(
            circuitName = "LPP-G3-1",
            projectId = projectId,
            loadKVA = 4.5,
            fedFrom = "DP-G3",
            panelType = "DB",
            sourceType = "PANEL",
            parentId = parentId,
            level = 1
        )

        val child1Id = repository.insertCircuit(child1).toInt()
        val child2Id = repository.insertCircuit(child2).toInt()
        val child3Id = repository.insertCircuit(child3).toInt()

        // Verify initial state: all 3 children reference parentId
        val initialCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        assertEquals(4, initialCircuits.size)
        val initialChildren = initialCircuits.filter { it.id != parentId }
        assertEquals(3, initialChildren.size)
        assertTrue(initialChildren.all { it.parentId == parentId })

        // 3. Delete the parent circuit using repository.deleteCircuit
        repository.deleteCircuit(parentWithId)

        // 4. Verify post-deletion state:
        // A. Parent row is completely removed
        val remainingCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        assertEquals(3, remainingCircuits.size)
        assertFalse(remainingCircuits.any { it.id == parentId })

        // B. All child circuits have parentId nullified (no dangling pointer to parentId)
        assertTrue("All children must have parentId == null after parent deletion", remainingCircuits.all { it.parentId == null })
        assertTrue("All children must have fedFrom reset to Transformer", remainingCircuits.all { it.fedFrom == "Transformer" })
        assertTrue("All children must have sourceType reset to TRANSFORMER", remainingCircuits.all { it.sourceType == "TRANSFORMER" })

        // C. Export and re-import test to verify zero integrity violations
        val exportedJsonStr = repository.exportProjectJson(projectId)
        val reimportResult = repository.importProjectFromJsonDetailed(exportedJsonStr)
        assertNotNull(reimportResult)
        assertEquals(ImportStatus.SUCCESS, reimportResult.status)
        assertEquals(3, reimportResult.importedCounts.circuits)
        assertTrue(reimportResult.errors.isEmpty())
    }
}

