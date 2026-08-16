package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class EngineeringRepository(
    private val db: AppDatabase,
    val circuitDao: CircuitDao,
    val transformerDao: TransformerDao
) : ProjectRevisionProvider {
    constructor(db: AppDatabase) : this(db, db.circuitDao(), db.transformerDao())

    override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
        val record = projectDao.getRevisionSnapshot(projectId)
            ?: throw NoSuchElementException("Project not found with id: $projectId")
        return ProjectRevisionSnapshot(
            projectId = record.projectId,
            revision = record.revision,
            updatedAt = record.updatedAt
        )
    }

    private suspend fun <T> withProjectMutation(projectId: String, block: suspend () -> T): T {
        return db.withTransaction {
            val result = block()
            val rowsUpdated = projectDao.incrementRevision(projectId, System.currentTimeMillis())
            if (rowsUpdated == 0) {
                val exists = projectDao.getProject(projectId) != null
                if (!exists) {
                    throw NoSuchElementException("Cannot advance revision: project '$projectId' does not exist.")
                } else {
                    throw IllegalStateException("Project revision overflow: maximum revision reached for project '$projectId'.")
                }
            }
            result
        }
    }

    val circuitsFlow: Flow<List<CircuitEntity>> = circuitDao.getAllCircuits()
    val transformersFlow: Flow<List<TransformerEntity>> = transformerDao.getAllTransformers()


    // Power Systems
    private val powerSystemsDao = db.powerSystemsDao()
    
    fun generatorSettingsFlow(projectId: String) = powerSystemsDao.getGeneratorSettings(projectId)
    fun generatorsFlow(projectId: String) = powerSystemsDao.getAllGeneratorSettings(projectId)
    suspend fun saveGeneratorSettings(settings: GeneratorEntity) = withProjectMutation(settings.projectId) {
        powerSystemsDao.saveGeneratorSettings(settings)
    }
    suspend fun deleteGenerator(projectId: String, generatorId: String) = withProjectMutation(projectId) {
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        circuits.filter { it.assignedGeneratorId == generatorId }.forEach { c ->
            circuitDao.insertCircuit(c.copy(assignedGeneratorId = null))
        }
        val atsList = powerSystemsDao.getAllAtsSettings(projectId).firstOrNull() ?: emptyList()
        atsList.filter { it.emergencySourceId == generatorId }.forEach { ats ->
            powerSystemsDao.saveAtsSettings(ats.copy(emergencySourceId = null))
        }
        powerSystemsDao.deleteGeneratorSettings(projectId, generatorId)
    }

    suspend fun createGeneratorAtomic(projectId: String, baseSettings: GeneratorEntity = GeneratorEntity()): GeneratorEntity = withProjectMutation(projectId) {
        val maxId = powerSystemsDao.getMaxGeneratorId(projectId)
        val nextId = maxId + 1
        val genIdStr = "GEN-%02d".format(nextId)
        val genNameStr = "Generator $nextId"
        val newEntity = baseSettings.copy(
            projectId = projectId,
            id = nextId,
            generatorId = genIdStr,
            generatorName = genNameStr
        )
        powerSystemsDao.saveGeneratorSettings(newEntity)
        newEntity
    }
    
    fun atsSettingsFlow(projectId: String) = powerSystemsDao.getAtsSettings(projectId)
    fun atsListFlow(projectId: String) = powerSystemsDao.getAllAtsSettings(projectId)
    suspend fun saveAtsSettings(settings: AtsEntity) = withProjectMutation(settings.projectId) {
        powerSystemsDao.saveAtsSettings(settings)
    }
    suspend fun deleteAts(projectId: String, atsId: String) = withProjectMutation(projectId) {
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        circuits.filter { it.assignedAtsId == atsId }.forEach { c ->
            circuitDao.insertCircuit(c.copy(assignedAtsId = null))
        }
        powerSystemsDao.deleteAtsSettings(projectId, atsId)
    }

    suspend fun createAtsAtomic(projectId: String, baseSettings: AtsEntity = AtsEntity()): AtsEntity = withProjectMutation(projectId) {
        val maxId = powerSystemsDao.getMaxAtsId(projectId)
        val nextId = maxId + 1
        val atsIdStr = "ATS-%02d".format(nextId)
        val atsNameStr = "ATS $nextId"
        val newEntity = baseSettings.copy(
            projectId = projectId,
            id = nextId,
            atsId = atsIdStr,
            atsName = atsNameStr
        )
        powerSystemsDao.saveAtsSettings(newEntity)
        newEntity
    }
    
    fun upsSettingsFlow(projectId: String) = powerSystemsDao.getUpsSettings(projectId)
    fun upsListFlow(projectId: String) = powerSystemsDao.getAllUpsSettings(projectId)
    suspend fun saveUpsSettings(settings: UpsEntity) = withProjectMutation(settings.projectId) {
        powerSystemsDao.saveUpsSettings(settings)
    }
    suspend fun deleteUps(projectId: String, upsId: String) = withProjectMutation(projectId) {
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        circuits.filter { it.assignedUpsId == upsId }.forEach { c ->
            circuitDao.insertCircuit(c.copy(assignedUpsId = null))
        }
        powerSystemsDao.deleteUpsSettings(projectId, upsId)
    }

    suspend fun createUpsAtomic(projectId: String, baseSettings: UpsEntity = UpsEntity()): UpsEntity = withProjectMutation(projectId) {
        val maxId = powerSystemsDao.getMaxUpsId(projectId)
        val nextId = maxId + 1
        val upsIdStr = "UPS-%02d".format(nextId)
        val upsNameStr = "UPS $nextId"
        val newEntity = baseSettings.copy(
            projectId = projectId,
            id = nextId,
            upsId = upsIdStr,
            upsName = upsNameStr
        )
        powerSystemsDao.saveUpsSettings(newEntity)
        newEntity
    }
    
    fun pfGlobalSettingsFlow(projectId: String) = powerSystemsDao.getPfGlobalSettings(projectId)
    suspend fun savePfGlobalSettings(settings: PfGlobalEntity) = withProjectMutation(settings.projectId) {
        powerSystemsDao.savePfGlobalSettings(settings)
    }
    
    fun pfTransformerSettingsFlow(projectId: String) = powerSystemsDao.getAllPfTransformerSettings(projectId)
    suspend fun savePfTransformerSettings(settings: PfTransformerEntity) = withProjectMutation(settings.projectId) {
        powerSystemsDao.savePfTransformerSettings(settings)
    }

    fun earthingSettingsFlow(projectId: String) = powerSystemsDao.getEarthingSettings(projectId)
    suspend fun saveEarthingSettings(settings: EarthingEntity) = withProjectMutation(settings.projectId) {
        powerSystemsDao.saveEarthingSettings(settings)
    }

    suspend fun getAllCircuits(): List<CircuitEntity> = circuitDao.getAllCircuitsList()
    suspend fun getAllTransformers(): List<TransformerEntity> = transformerDao.getAllTransformersList()

    suspend fun getCircuitById(id: Int): CircuitEntity? = circuitDao.getCircuitById(id)
    suspend fun getCircuitByName(name: String): CircuitEntity? = circuitDao.getCircuitByName(name)
    suspend fun getTransformerById(projectId: String, id: String): TransformerEntity? = transformerDao.getTransformerById(projectId, id)

    suspend fun insertCircuit(circuit: CircuitEntity) = withProjectMutation(circuit.projectId) {
        circuitDao.insertCircuit(circuit)
    }
    suspend fun insertCircuits(circuits: List<CircuitEntity>) {
        if (circuits.isEmpty()) return
        val projectIds = circuits.map { it.projectId }.distinct()
        if (projectIds.size == 1) {
            withProjectMutation(projectIds.first()) {
                circuitDao.insertCircuits(circuits)
            }
        } else {
            db.withTransaction {
                circuitDao.insertCircuits(circuits)
                projectIds.forEach { pid ->
                    projectDao.incrementRevision(pid, System.currentTimeMillis())
                }
            }
        }
    }
    suspend fun updateCircuit(circuit: CircuitEntity) = withProjectMutation(circuit.projectId) {
        circuitDao.updateCircuit(circuit)
    }
    suspend fun deleteCircuit(circuit: CircuitEntity) = withProjectMutation(circuit.projectId) {
        circuitDao.deleteCircuitWithCascade(circuit)
    }
    suspend fun deleteAllCircuits() = circuitDao.deleteAllCircuits()
    suspend fun deleteCircuitsForProject(projectId: String) = withProjectMutation(projectId) {
        circuitDao.deleteCircuitsForProject(projectId)
    }

    suspend fun insertTransformer(transformer: TransformerEntity) = withProjectMutation(transformer.projectId) {
        transformerDao.insertTransformer(transformer)
    }
    suspend fun insertTransformers(transformers: List<TransformerEntity>) {
        if (transformers.isEmpty()) return
        val projectIds = transformers.map { it.projectId }.distinct()
        if (projectIds.size == 1) {
            withProjectMutation(projectIds.first()) {
                transformerDao.insertTransformers(transformers)
            }
        } else {
            db.withTransaction {
                transformerDao.insertTransformers(transformers)
                projectIds.forEach { pid ->
                    projectDao.incrementRevision(pid, System.currentTimeMillis())
                }
            }
        }
    }
    suspend fun updateTransformer(transformer: TransformerEntity) = withProjectMutation(transformer.projectId) {
        transformerDao.updateTransformer(transformer)
    }
    suspend fun deleteTransformer(transformer: TransformerEntity) = withProjectMutation(transformer.projectId) {
        transformerDao.deleteTransformer(transformer)
    }
    suspend fun deleteAllTransformers() = transformerDao.deleteAllTransformers()
    suspend fun deleteTransformersForProject(projectId: String) = withProjectMutation(projectId) {
        transformerDao.deleteTransformersForProject(projectId)
    }


    // Project management
    val projectDao = db.projectDao()
    val appStateDao = db.appStateDao()

    fun getAllProjects() = projectDao.getAllProjects()
    fun observeProject(id: String) = projectDao.observeProject(id)

    suspend fun updateProjectImbalanceLimit(projectId: String, limitPct: Double) = withProjectMutation(projectId) {
        val proj = projectDao.getProject(projectId) ?: return@withProjectMutation
        val updated = proj.copy(
            defaultPhaseImbalanceLimitPct = limitPct,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(updated)
    }

    suspend fun updateCircuitImbalanceOverride(circuitId: Int, limitOverridePct: Double?) {
        val circuit = circuitDao.getCircuitById(circuitId) ?: return
        withProjectMutation(circuit.projectId) {
            val updated = circuit.copy(phaseImbalanceLimitOverridePct = limitOverridePct)
            circuitDao.insertCircuit(updated)
        }
    }

    suspend fun createProject(name: String, client: String? = null): String {
        val id = "PRJ_" + System.currentTimeMillis()
        val now = System.currentTimeMillis()
        projectDao.upsertProject(
            ProjectEntity(
                projectId = id,
                projectName = name,
                clientName = client,
                createdAt = now,
                updatedAt = now,
                revision = 1L
            )
        )
        return id
    }

    suspend fun renameProject(id: String, name: String) = withProjectMutation(id) {
        projectDao.rename(id, name, System.currentTimeMillis())
    }

    suspend fun deleteProject(id: String) {
        db.withTransaction {
            circuitDao.deleteCircuitsForProject(id)
            transformerDao.deleteTransformersForProject(id)
            powerSystemsDao.deleteGeneratorsForProject(id)
            powerSystemsDao.deleteAtsForProject(id)
            powerSystemsDao.deleteUpsForProject(id)
            powerSystemsDao.deletePfGlobalForProject(id)
            powerSystemsDao.deletePfTransformerForProject(id)
            conduitDao.deleteConduitSettingsForProject(id)
            powerSystemsDao.deleteEarthingForProject(id)
            sldNodePositionDao.clearPositionsForProject(id)
            projectDao.deleteProject(id)
            if (appStateDao.getActiveProjectId() == id) {
                appStateDao.setState(AppStateEntity(0, null))
            }
        }
    }

    // Active state
    suspend fun setActiveProject(id: String?) = appStateDao.setState(AppStateEntity(0, id))
    suspend fun getActiveProjectId(): String? = appStateDao.getActiveProjectId()
    suspend fun getActiveProject(): ProjectEntity? {
        val activeId = appStateDao.getActiveProjectId() ?: return null
        return projectDao.getProject(activeId)
    }
    suspend fun getAllProjectsList(): List<ProjectEntity> = projectDao.getAllProjectsList()
    fun observeAppState() = appStateDao.observeState()

    // Project data
    fun circuitsForProject(id: String) = circuitDao.getCircuitsForProject(id)
    fun transformersForProject(id: String) = transformerDao.getTransformersForProject(id)

    private val sldNodePositionDao = db.sldNodePositionDao()
    private val conduitDao = db.conduitDao()

    // Complete JSON V2 Export for any user project
    suspend fun exportProjectJson(projectId: String): String {
        val proj = projectDao.getProject(projectId) ?: return "{}"
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        val transformers = transformerDao.getTransformersListForProject(projectId)
        val generators = powerSystemsDao.getAllGeneratorsList(projectId)
        val atsList = powerSystemsDao.getAllAtsList(projectId)
        val upsList = powerSystemsDao.getAllUpsList(projectId)
        val pfGlobal = powerSystemsDao.getPfGlobalSettingsDirect(projectId)
        val pfTransformers = powerSystemsDao.getAllPfTransformerSettingsList(projectId)
        val conduitSettings = conduitDao.getConduitSettingsListForProject(projectId)
        val earthing = powerSystemsDao.getEarthingSettingsDirect(projectId)
        val sldPositions = sldNodePositionDao.getPositionsForProjectSync(projectId)

        val root = JSONObject()
        root.put("schema", "VoltAtlasProject")
        root.put("version", 2)
        root.put("appName", "VoltAtlas")
        root.put("appVersion", "1.0")
        root.put("exportedAt", System.currentTimeMillis())

        val projObj = JSONObject()
        projObj.put("projectId", proj.projectId)
        projObj.put("projectName", proj.projectName)
        projObj.put("projectCode", proj.projectCode ?: "")
        projObj.put("clientName", proj.clientName ?: "")
        projObj.put("defaultPhaseImbalanceLimitPct", proj.defaultPhaseImbalanceLimitPct)
        projObj.put("createdAt", proj.createdAt)
        projObj.put("updatedAt", proj.updatedAt)
        root.put("project", projObj)

        val transformersArr = JSONArray()
        transformers.forEach { t ->
            val obj = JSONObject()
            obj.put("transformerId", t.transformerId)
            obj.put("transformerName", t.transformerName)
            obj.put("kVA", t.kVA)
            obj.put("voltage", t.voltage)
            obj.put("percentZ", t.percentZ)
            obj.put("xr", t.xr)
            obj.put("txType", t.txType)
            obj.put("sourceType", t.sourceType)
            obj.put("status", t.status)
            obj.put("sizeMode", t.sizeMode)
            obj.put("manualKVA", t.manualKVA)
            obj.put("demandFactor", t.demandFactor)
            obj.put("diversity", t.diversity)
            obj.put("growth", t.growth)
            obj.put("safety", t.safety)
            obj.put("vectorGroup", t.vectorGroup)
            obj.put("z0OverZ1", t.z0OverZ1)
            obj.put("pf", t.pf)
            transformersArr.put(obj)
        }
        root.put("transformers", transformersArr)

        val circuitsArr = JSONArray()
        circuits.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("circuitName", c.circuitName)
            obj.put("loadKVA", c.loadKVA)
            obj.put("fedFrom", c.fedFrom)
            obj.put("transformerId", c.transformerId)
            obj.put("loadType", c.loadType)
            obj.put("phases", c.phases)
            obj.put("voltageV", c.voltageV)
            obj.put("pf", c.pf)
            obj.put("safetyFactor", c.safetyFactor)
            obj.put("manufacturer", c.manufacturer)
            obj.put("cableType", c.cableType)
            obj.put("installation", c.installation)
            obj.put("parallelRuns", c.parallelRuns)
            obj.put("airTempC", c.airTempC)
            obj.put("gndTempC", c.gndTempC)
            obj.put("burialDepth_m", c.burialDepth_m)
            obj.put("soilRes", c.soilRes)
            obj.put("noCircuits", c.noCircuits)
            obj.put("formation", c.formation)
            obj.put("spacing", c.spacing)
            obj.put("length_m", c.length_m)
            obj.put("maxVDpct", c.maxVDpct)
            if (c.overrideDeviceType != null) obj.put("overrideDeviceType", c.overrideDeviceType)
            if (c.overrideDeviceSize != null) obj.put("overrideDeviceSize", c.overrideDeviceSize)
            if (c.overrideCableSize != null) obj.put("overrideCableSize", c.overrideCableSize)
            if (c.overrideNeutralSize != null) obj.put("overrideNeutralSize", c.overrideNeutralSize)
            if (c.overrideEarthSize != null) obj.put("overrideEarthSize", c.overrideEarthSize)
            obj.put("isHarmonic", c.isHarmonic)
            if (c.assignedPhase != null) obj.put("assignedPhase", c.assignedPhase)
            obj.put("upsBacked", c.upsBacked)
            obj.put("demandFactor", c.demandFactor)
            if (c.diversityGroup != null) obj.put("diversityGroup", c.diversityGroup)
            obj.put("generatorIncluded", c.generatorIncluded)
            obj.put("atsIncluded", c.atsIncluded)
            if (c.assignedGeneratorId != null) obj.put("assignedGeneratorId", c.assignedGeneratorId)
            if (c.assignedAtsId != null) obj.put("assignedAtsId", c.assignedAtsId)
            if (c.assignedUpsId != null) obj.put("assignedUpsId", c.assignedUpsId)
            obj.put("loadNature", c.loadNature)
            if (c.startingMethod != null) obj.put("startingMethod", c.startingMethod)
            if (c.startingCurrentMultiplier != null) obj.put("startingCurrentMultiplier", c.startingCurrentMultiplier)
            obj.put("active", c.active)
            if (c.panelType != null) obj.put("panelType", c.panelType)
            if (c.parentId != null) obj.put("parentId", c.parentId)
            if (c.level != null) obj.put("level", c.level)
            if (c.sourceType != null) obj.put("sourceType", c.sourceType)
            if (c.directLoadConfirmed != null) obj.put("directLoadConfirmed", c.directLoadConfirmed)
            if (c.phaseImbalanceLimitOverridePct != null) obj.put("phaseImbalanceLimitOverridePct", c.phaseImbalanceLimitOverridePct)
            if (c.loadCategory != null) obj.put("loadCategory", c.loadCategory)
            if (c.busbarRatingA != null) obj.put("busbarRatingA", c.busbarRatingA)
            if (c.scRatingKA != null) obj.put("scRatingKA", c.scRatingKA)
            if (c.mainIncomingType != null) obj.put("mainIncomingType", c.mainIncomingType)
            if (c.mainIncomingCount != null) obj.put("mainIncomingCount", c.mainIncomingCount)
            if (c.ipRating != null) obj.put("ipRating", c.ipRating)
            if (c.panelLocation != null) obj.put("panelLocation", c.panelLocation)
            if (c.cableEntry != null) obj.put("cableEntry", c.cableEntry)
            if (c.feederDescription != null) obj.put("feederDescription", c.feederDescription)
            if (c.numberOfWays != null) obj.put("numberOfWays", c.numberOfWays)
            obj.put("feederType", c.feederType)
            if (c.buswayMfg != null) obj.put("buswayMfg", c.buswayMfg)
            if (c.buswayModel != null) obj.put("buswayModel", c.buswayModel)
            circuitsArr.put(obj)
        }
        root.put("circuits", circuitsArr)

        val genArr = JSONArray()
        generators.forEach { gen ->
            val obj = JSONObject()
            obj.put("id", gen.id)
            obj.put("generatorId", gen.generatorId)
            obj.put("generatorName", gen.generatorName)
            obj.put("additionalEssentialLoadKW", gen.additionalEssentialLoadKW)
            obj.put("largestMotorKW", gen.largestMotorKW)
            obj.put("motorStartMethod", gen.motorStartMethod)
            obj.put("motorPf", gen.motorPf)
            obj.put("generatorPf", gen.generatorPf)
            obj.put("demandFactor", gen.demandFactor)
            obj.put("diversityFactor", gen.diversityFactor)
            obj.put("stepLoadPct", gen.stepLoadPct)
            obj.put("oversizingFactor", gen.oversizingFactor)
            obj.put("enableManualOverride", gen.enableManualOverride)
            if (gen.manualSizeKVA != null) obj.put("manualSizeKVA", gen.manualSizeKVA)
            genArr.put(obj)
        }
        root.put("generators", genArr)

        val atsArr = JSONArray()
        atsList.forEach { ats ->
            val obj = JSONObject()
            obj.put("id", ats.id)
            obj.put("atsId", ats.atsId)
            obj.put("atsName", ats.atsName)
            obj.put("systemVoltage", ats.systemVoltage)
            obj.put("systemFrequency", ats.systemFrequency)
            obj.put("numberOfSources", ats.numberOfSources)
            obj.put("atsLocation", ats.atsLocation)
            obj.put("transferType", ats.transferType)
            obj.put("utilizationCategory", ats.utilizationCategory)
            obj.put("atsClass", ats.atsClass)
            obj.put("loadCoverage", ats.loadCoverage)
            obj.put("ambientTemp", ats.ambientTemp)
            obj.put("designSafetyFactor", ats.designSafetyFactor)
            obj.put("futureGrowthFactor", ats.futureGrowthFactor)
            obj.put("approvedManufacturer", ats.approvedManufacturer)
            obj.put("mainsFailureDetectionDelay", ats.mainsFailureDetectionDelay)
            obj.put("generatorStartSignalDelay", ats.generatorStartSignalDelay)
            obj.put("generatorWarmUp", ats.generatorWarmUp)
            obj.put("mainsReturnDetectionDelay", ats.mainsReturnDetectionDelay)
            obj.put("reTransferDelay", ats.reTransferDelay)
            obj.put("generatorCooldown", ats.generatorCooldown)
            obj.put("enclosureIpRating", ats.enclosureIpRating)
            if (ats.normalSourceId != null) obj.put("normalSourceId", ats.normalSourceId)
            if (ats.emergencySourceId != null) obj.put("emergencySourceId", ats.emergencySourceId)
            if (ats.outputBoardId != null) obj.put("outputBoardId", ats.outputBoardId)
            obj.put("enableManualOverride", ats.enableManualOverride)
            if (ats.manualSizeAmp != null) obj.put("manualSizeAmp", ats.manualSizeAmp)
            atsArr.put(obj)
        }
        root.put("ats", atsArr)

        val upsArr = JSONArray()
        upsList.forEach { ups ->
            val obj = JSONObject()
            obj.put("id", ups.id)
            obj.put("upsId", ups.upsId)
            obj.put("upsName", ups.upsName)
            obj.put("systemVoltage", ups.systemVoltage)
            obj.put("systemFrequency", ups.systemFrequency)
            obj.put("upsPf", ups.upsPf)
            obj.put("upsEfficiency", ups.upsEfficiency)
            obj.put("batteryType", ups.batteryType)
            obj.put("autonomyMinutes", ups.autonomyMinutes)
            obj.put("batteryEodVoltage", ups.batteryEodVoltage)
            obj.put("ambientTemp", ups.ambientTemp)
            obj.put("tempDeratingFactor", ups.tempDeratingFactor)
            obj.put("agingFactor", ups.agingFactor)
            obj.put("designMarginPct", ups.designMarginPct)
            obj.put("enableManualOverride", ups.enableManualOverride)
            if (ups.manualSizeKVA != null) obj.put("manualSizeKVA", ups.manualSizeKVA)
            upsArr.put(obj)
        }
        root.put("ups", upsArr)

        if (pfGlobal != null) {
            val obj = JSONObject()
            obj.put("id", pfGlobal.id)
            obj.put("targetPf", pfGlobal.targetPf)
            obj.put("kvarStepSize", pfGlobal.kvarStepSize)
            obj.put("systemVoltage", pfGlobal.systemVoltage)
            obj.put("annualOperatingHours", pfGlobal.annualOperatingHours)
            obj.put("energyCost", pfGlobal.energyCost)
            obj.put("lossReductionPct", pfGlobal.lossReductionPct)
            obj.put("unityToleranceKvar", pfGlobal.unityToleranceKvar)
            root.put("pfGlobal", obj)
        }

        val pfTxArr = JSONArray()
        pfTransformers.forEach { pft ->
            val obj = JSONObject()
            obj.put("transformerId", pft.transformerId)
            obj.put("harmonicFilter", pft.harmonicFilter)
            obj.put("detuningReactor", pft.detuningReactor)
            pfTxArr.put(obj)
        }
        root.put("pfTransformers", pfTxArr)

        val conduitArr = JSONArray()
        conduitSettings.forEach { cs ->
            val obj = JSONObject()
            obj.put("circuitId", cs.circuitId)
            obj.put("routingMode", cs.routingMode)
            obj.put("designProfile", cs.designProfile)
            obj.put("customFillLimitPct", cs.customFillLimitPct)
            obj.put("isDirectBuried", cs.isDirectBuried)
            obj.put("cpcArrangement", cs.cpcArrangement)
            if (cs.manualConduitOverride != null) obj.put("manualConduitOverride", cs.manualConduitOverride)
            if (cs.manualCpcOD != null) obj.put("manualCpcOD", cs.manualCpcOD)
            conduitArr.put(obj)
        }
        root.put("conduitSettings", conduitArr)

        if (earthing != null) {
            val obj = JSONObject()
            obj.put("id", earthing.id)
            obj.put("soilResistivity_ohmM", earthing.soilResistivity_ohmM)
            obj.put("rodLength_m", earthing.rodLength_m)
            obj.put("rodDiameter_mm", earthing.rodDiameter_mm)
            obj.put("rodSpacing_m", earthing.rodSpacing_m)
            obj.put("ringConductor_mm2", earthing.ringConductor_mm2)
            obj.put("ringBurialDepth_m", earthing.ringBurialDepth_m)
            obj.put("kFactor", earthing.kFactor)
            obj.put("projectType", earthing.projectType)
            obj.put("disconnectionTime_s", earthing.disconnectionTime_s)
            if (earthing.targetResistanceOverride_ohm != null) obj.put("targetResistanceOverride_ohm", earthing.targetResistanceOverride_ohm)
            obj.put("soilResistivitySource", earthing.soilResistivitySource)
            obj.put("designMarginPct", earthing.designMarginPct)
            obj.put("isAutoDesignEnabled", earthing.isAutoDesignEnabled)
            if (earthing.siteLength_m != null) obj.put("siteLength_m", earthing.siteLength_m)
            if (earthing.siteWidth_m != null) obj.put("siteWidth_m", earthing.siteWidth_m)
            if (earthing.autoDesignAppliedConfigId != null) obj.put("autoDesignAppliedConfigId", earthing.autoDesignAppliedConfigId)
            root.put("earthingSettings", obj)
        }

        val sldArr = JSONArray()
        sldPositions.forEach { pos ->
            val obj = JSONObject()
            obj.put("nodeId", pos.nodeId)
            obj.put("x", pos.x)
            obj.put("y", pos.y)
            sldArr.put(obj)
        }
        root.put("sldNodePositions", sldArr)

        return root.toString(2)
    }

    private data class ParsedCircuitWithSourceId(
        val sourceId: Int,
        val entity: CircuitEntity,
        val originalParentId: Int?,
        val originalFedFrom: String
    )

    suspend fun importProjectFromJsonDetailed(jsonString: String): ProjectImportResult {
        return try {
            val root = JSONObject(jsonString)
            val schema = root.optString("schema", "")
            val version = root.optInt("version", if (schema == "VoltAtlasProject") 2 else 1)
            val notices = mutableListOf<MigrationNotice>()
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            val projObj = root.optJSONObject("project")
            val rawName = projObj?.optString("projectName")?.takeIf { it.isNotBlank() } ?: "Imported Project"
            val rawClient = projObj?.optString("clientName")?.takeIf { it.isNotBlank() }
            val rawCode = projObj?.optString("projectCode")?.takeIf { it.isNotBlank() }
            val imbalanceLimit = projObj?.optDouble("defaultPhaseImbalanceLimitPct", 20.0) ?: 20.0
            val createdAt = projObj?.optLong("createdAt", System.currentTimeMillis()) ?: System.currentTimeMillis()
            val updatedAt = projObj?.optLong("updatedAt", System.currentTimeMillis()) ?: System.currentTimeMillis()

            val newProjectId = "PRJ_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().take(6)

            var importedCircuitCount = 0
            var importedTxCount = 0
            var importedGenCount = 0
            var importedAtsCount = 0
            var importedUpsCount = 0
            var importedPfGlobalCount = 0
            var importedPfTxCount = 0
            var importedConduitCount = 0
            var importedEarthingCount = 0
            var importedSldCount = 0

            db.withTransaction {
                // 1. Project Entity
                projectDao.upsertProject(
                    ProjectEntity(
                        projectId = newProjectId,
                        projectName = rawName,
                        projectCode = rawCode,
                        clientName = rawClient,
                        defaultPhaseImbalanceLimitPct = imbalanceLimit,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        revision = 1L
                    )
                )

                // 2. Transformers
                if (root.has("transformers")) {
                    val arr = root.getJSONArray("transformers")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val tx = TransformerEntity(
                            transformerId = obj.optString("transformerId", "TX-${i + 1}"),
                            projectId = newProjectId,
                            transformerName = obj.optString("transformerName", "Transformer ${i + 1}"),
                            kVA = obj.optDouble("kVA", 1000.0),
                            voltage = obj.optInt("voltage", 400),
                            percentZ = obj.optDouble("percentZ", 5.0),
                            xr = obj.optDouble("xr", 7.0),
                            txType = obj.optString("txType", "Dry Type (Cast Resin)"),
                            sourceType = obj.optString("sourceType", "Medium Voltage"),
                            status = obj.optString("status", "Active"),
                            sizeMode = obj.optString("sizeMode", "Auto"),
                            manualKVA = obj.optDouble("manualKVA", 1000.0),
                            demandFactor = obj.optDouble("demandFactor", 0.8),
                            diversity = obj.optDouble("diversity", 1.0),
                            growth = obj.optDouble("growth", 1.0),
                            safety = obj.optDouble("safety", 1.0),
                            vectorGroup = obj.optString("vectorGroup", "Dyn11"),
                            z0OverZ1 = obj.optDouble("z0OverZ1", 1.0),
                            pf = obj.optDouble("pf", 0.8)
                        )
                        transformerDao.insertTransformer(tx)
                        importedTxCount++
                        if (version == 1) {
                            if (!obj.has("vectorGroup")) notices.add(MigrationNotice("Transformer", tx.transformerId, "vectorGroup", MigrationFieldClassification.DEFAULTED_BY_SCHEMA, "Defaulted vectorGroup to Dyn11."))
                            if (!obj.has("z0OverZ1")) notices.add(MigrationNotice("Transformer", tx.transformerId, "z0OverZ1", MigrationFieldClassification.DEFAULTED_BY_SCHEMA, "Defaulted z0OverZ1 to 1.0."))
                        }
                    }
                }

                // 3. Circuits parsing & remapping
                val circuitIdMap = mutableMapOf<Int, Int>()
                val parsedCircuits = mutableListOf<ParsedCircuitWithSourceId>()

                if (root.has("circuits")) {
                    val arr = root.getJSONArray("circuits")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val sourceId = if (obj.has("id") && obj.optInt("id") > 0) obj.getInt("id") else (i + 1)
                        val originalParentId = if (obj.has("parentId") && !obj.isNull("parentId") && obj.optInt("parentId") != -1) obj.getInt("parentId") else null
                        val fedFrom = obj.optString("fedFrom", "Transformer")

                        val c = CircuitEntity(
                            id = 0, // Generated by Room
                            circuitName = obj.optString("circuitName", "Circuit-${i + 1}"),
                            projectId = newProjectId,
                            loadKVA = obj.optDouble("loadKVA", 10.0),
                            fedFrom = fedFrom,
                            transformerId = obj.optString("transformerId", "TX-01"),
                            loadType = obj.optString("loadType", "Normal"),
                            phases = obj.optString("phases", "3P+N"),
                            voltageV = obj.optInt("voltageV", 400),
                            pf = obj.optDouble("pf", 0.85),
                            safetyFactor = obj.optDouble("safetyFactor", 1.0),
                            manufacturer = obj.optString("manufacturer", "Elsewedy Electric"),
                            cableType = obj.optString("cableType", "CU/XLPE/PVC"),
                            installation = obj.optString("installation", "B1"),
                            airTempC = obj.optInt("airTempC", 40),
                            gndTempC = obj.optInt("gndTempC", 40),
                            burialDepth_m = obj.optDouble("burialDepth_m", 0.6),
                            soilRes = obj.optInt("soilRes", 125),
                            noCircuits = obj.optInt("noCircuits", 1),
                            formation = obj.optString("formation", "Flat"),
                            spacing = obj.optString("spacing", "Touching"),
                            parallelRuns = obj.optInt("parallelRuns", 1),
                            length_m = obj.optDouble("length_m", 50.0),
                            maxVDpct = obj.optDouble("maxVDpct", 0.05),
                            overrideDeviceType = obj.optString("overrideDeviceType").takeIf { !it.isNullOrBlank() },
                            overrideDeviceSize = if (obj.has("overrideDeviceSize") && !obj.isNull("overrideDeviceSize") && obj.optDouble("overrideDeviceSize") > 0) obj.optDouble("overrideDeviceSize") else null,
                            overrideCableSize = obj.optString("overrideCableSize").takeIf { !it.isNullOrBlank() },
                            overrideNeutralSize = obj.optString("overrideNeutralSize").takeIf { !it.isNullOrBlank() },
                            overrideEarthSize = obj.optString("overrideEarthSize").takeIf { !it.isNullOrBlank() },
                            isHarmonic = obj.optBoolean("isHarmonic", false),
                            assignedPhase = obj.optString("assignedPhase").takeIf { !it.isNullOrBlank() },
                            upsBacked = obj.optBoolean("upsBacked", false),
                            demandFactor = obj.optDouble("demandFactor", 1.0),
                            diversityGroup = obj.optString("diversityGroup").takeIf { !it.isNullOrBlank() },
                            generatorIncluded = obj.optBoolean("generatorIncluded", false),
                            atsIncluded = obj.optBoolean("atsIncluded", false),
                            assignedGeneratorId = obj.optString("assignedGeneratorId").takeIf { !it.isNullOrBlank() },
                            assignedAtsId = obj.optString("assignedAtsId").takeIf { !it.isNullOrBlank() },
                            assignedUpsId = obj.optString("assignedUpsId").takeIf { !it.isNullOrBlank() },
                            loadNature = obj.optString("loadNature", "Static"),
                            startingMethod = obj.optString("startingMethod").takeIf { !it.isNullOrBlank() },
                            startingCurrentMultiplier = if (obj.has("startingCurrentMultiplier") && !obj.isNull("startingCurrentMultiplier")) obj.optDouble("startingCurrentMultiplier") else null,
                            active = obj.optBoolean("active", true),
                            panelType = obj.optString("panelType").takeIf { !it.isNullOrBlank() },
                            parentId = null, // Will be resolved and updated in second pass
                            level = if (obj.has("level") && !obj.isNull("level")) obj.optInt("level") else null,
                            sourceType = obj.optString("sourceType").takeIf { !it.isNullOrBlank() },
                            directLoadConfirmed = if (obj.has("directLoadConfirmed") && !obj.isNull("directLoadConfirmed")) obj.optBoolean("directLoadConfirmed") else null,
                            phaseImbalanceLimitOverridePct = if (obj.has("phaseImbalanceLimitOverridePct") && !obj.isNull("phaseImbalanceLimitOverridePct")) obj.optDouble("phaseImbalanceLimitOverridePct") else null,
                            loadCategory = obj.optString("loadCategory").takeIf { !it.isNullOrBlank() },
                            busbarRatingA = if (obj.has("busbarRatingA") && !obj.isNull("busbarRatingA")) obj.optDouble("busbarRatingA") else null,
                            scRatingKA = if (obj.has("scRatingKA") && !obj.isNull("scRatingKA")) obj.optDouble("scRatingKA") else null,
                            mainIncomingType = obj.optString("mainIncomingType").takeIf { !it.isNullOrBlank() },
                            mainIncomingCount = if (obj.has("mainIncomingCount") && !obj.isNull("mainIncomingCount")) obj.optInt("mainIncomingCount") else null,
                            ipRating = obj.optString("ipRating").takeIf { !it.isNullOrBlank() },
                            panelLocation = obj.optString("panelLocation").takeIf { !it.isNullOrBlank() },
                            cableEntry = obj.optString("cableEntry").takeIf { !it.isNullOrBlank() },
                            feederDescription = obj.optString("feederDescription").takeIf { !it.isNullOrBlank() },
                            numberOfWays = if (obj.has("numberOfWays") && !obj.isNull("numberOfWays")) obj.optInt("numberOfWays") else null,
                            feederType = obj.optString("feederType", "CABLE"),
                            buswayMfg = obj.optString("buswayMfg").takeIf { !it.isNullOrBlank() },
                            buswayModel = obj.optString("buswayModel").takeIf { !it.isNullOrBlank() }
                        )

                        val generatedId = circuitDao.insertCircuit(c).toInt()
                        circuitIdMap[sourceId] = generatedId
                        parsedCircuits.add(ParsedCircuitWithSourceId(sourceId, c.copy(id = generatedId), originalParentId, fedFrom))
                        importedCircuitCount++
                    }

                    // Second pass: remap parentId
                    for (parsed in parsedCircuits) {
                        val currentEntity = parsed.entity
                        if (version == 2) {
                            if (parsed.originalParentId != null) {
                                val remappedParentId = circuitIdMap[parsed.originalParentId]
                                if (remappedParentId == null) {
                                    throw IllegalArgumentException("Unresolved parentId ${parsed.originalParentId} for circuit '${currentEntity.circuitName}'. Integrity violation.")
                                }
                                circuitDao.updateCircuit(currentEntity.copy(parentId = remappedParentId))
                            }
                        } else {
                            // Version 1 best-effort migration
                            if (parsed.originalParentId != null && circuitIdMap.containsKey(parsed.originalParentId)) {
                                val remappedParentId = circuitIdMap[parsed.originalParentId]
                                circuitDao.updateCircuit(currentEntity.copy(parentId = remappedParentId))
                                notices.add(MigrationNotice("Circuit", currentEntity.circuitName, "parentId", MigrationFieldClassification.RESTORED, "Preserved parentId $remappedParentId from V1 source."))
                            } else if (!parsed.originalFedFrom.equals("Transformer", ignoreCase = true) && parsed.originalFedFrom.isNotBlank()) {
                                // Deterministic fedFrom fallback: exact name + same project + exactly one match
                                val candidateMatches = parsedCircuits.filter { it.entity.circuitName == parsed.originalFedFrom }
                                if (candidateMatches.size == 1) {
                                    val matchedParentId = candidateMatches.first().entity.id
                                    circuitDao.updateCircuit(currentEntity.copy(parentId = matchedParentId))
                                    notices.add(MigrationNotice("Circuit", currentEntity.circuitName, "parentId", MigrationFieldClassification.MIGRATED, "Resolved fedFrom '${parsed.originalFedFrom}' to parent circuit ID $matchedParentId based on unique exact name match."))
                                } else if (candidateMatches.isEmpty()) {
                                    notices.add(MigrationNotice("Circuit", currentEntity.circuitName, "parentId", MigrationFieldClassification.UNAVAILABLE_FROM_V1, "fedFrom '${parsed.originalFedFrom}' could not be resolved to any parent circuit; parentId remains unassigned."))
                                    warnings.add("Circuit '${currentEntity.circuitName}': fedFrom '${parsed.originalFedFrom}' could not be resolved to a parent circuit.")
                                } else {
                                    notices.add(MigrationNotice("Circuit", currentEntity.circuitName, "parentId", MigrationFieldClassification.INVALID, "fedFrom '${parsed.originalFedFrom}' is ambiguous (${candidateMatches.size} matching circuits); parentId remains unassigned."))
                                    warnings.add("Circuit '${currentEntity.circuitName}': fedFrom '${parsed.originalFedFrom}' is ambiguous matching multiple circuits.")
                                }
                            }
                        }
                    }
                }

                // 4. Generators
                if (root.has("generators")) {
                    val arr = root.getJSONArray("generators")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val idNum = obj.optInt("id", i + 1)
                        val gen = GeneratorEntity(
                            projectId = newProjectId,
                            id = idNum,
                            generatorId = obj.optString("generatorId", "GEN-%02d".format(idNum)),
                            generatorName = obj.optString("generatorName", "Generator $idNum"),
                            additionalEssentialLoadKW = obj.optDouble("additionalEssentialLoadKW", 0.0),
                            largestMotorKW = obj.optDouble("largestMotorKW", 0.0),
                            motorStartMethod = obj.optString("motorStartMethod", "DOL"),
                            motorPf = obj.optDouble("motorPf", 0.85),
                            generatorPf = obj.optDouble("generatorPf", 0.8),
                            demandFactor = obj.optDouble("demandFactor", 0.8),
                            diversityFactor = obj.optDouble("diversityFactor", 1.0),
                            stepLoadPct = obj.optDouble("stepLoadPct", 50.0),
                            oversizingFactor = obj.optDouble("oversizingFactor", 1.0),
                            enableManualOverride = obj.optBoolean("enableManualOverride", false),
                            manualSizeKVA = if (obj.has("manualSizeKVA") && !obj.isNull("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
                        )
                        powerSystemsDao.saveGeneratorSettings(gen)
                        importedGenCount++
                    }
                } else if (root.has("generatorSettings")) {
                    val obj = root.getJSONObject("generatorSettings")
                    val gen = GeneratorEntity(
                        projectId = newProjectId,
                        id = 1,
                        generatorId = "GEN-01",
                        generatorName = "Generator 1",
                        additionalEssentialLoadKW = obj.optDouble("additionalEssentialLoadKW", 0.0),
                        largestMotorKW = obj.optDouble("largestMotorKW", 0.0),
                        motorStartMethod = obj.optString("motorStartMethod", "DOL"),
                        motorPf = obj.optDouble("motorPf", 0.85),
                        generatorPf = obj.optDouble("generatorPf", 0.8),
                        demandFactor = obj.optDouble("demandFactor", 0.8),
                        diversityFactor = obj.optDouble("diversityFactor", 1.0),
                        stepLoadPct = obj.optDouble("stepLoadPct", 50.0),
                        oversizingFactor = obj.optDouble("oversizingFactor", 1.0),
                        enableManualOverride = obj.optBoolean("enableManualOverride", false),
                        manualSizeKVA = if (obj.has("manualSizeKVA") && !obj.isNull("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
                    )
                    powerSystemsDao.saveGeneratorSettings(gen)
                    importedGenCount++
                }

                // 5. ATS
                val rawAtsList = if (root.has("ats")) root.getJSONArray("ats") else null
                val rawAtsSingle = if (rawAtsList == null && root.has("atsSettings")) root.getJSONObject("atsSettings") else null

                if (rawAtsList != null) {
                    for (i in 0 until rawAtsList.length()) {
                        val obj = rawAtsList.getJSONObject(i)
                        val idNum = obj.optInt("id", i + 1)
                        val rawOutputBoard = if (obj.has("outputBoardId") && !obj.isNull("outputBoardId") && obj.optInt("outputBoardId") != -1) obj.getInt("outputBoardId") else null
                        val remappedOutputBoard = if (rawOutputBoard != null) {
                            if (circuitIdMap.containsKey(rawOutputBoard)) {
                                circuitIdMap[rawOutputBoard]
                            } else if (version == 2) {
                                throw IllegalArgumentException("Unresolved ATS outputBoardId $rawOutputBoard for ATS '${obj.optString("atsName")}'. Integrity violation.")
                            } else {
                                notices.add(MigrationNotice("ATS", obj.optString("atsName", "ATS $idNum"), "outputBoardId", MigrationFieldClassification.UNAVAILABLE_FROM_V1, "Unresolved outputBoardId $rawOutputBoard cleared."))
                                null
                            }
                        } else null

                        val ats = AtsEntity(
                            projectId = newProjectId,
                            id = idNum,
                            atsId = obj.optString("atsId", "ATS-%02d".format(idNum)),
                            atsName = obj.optString("atsName", "ATS $idNum"),
                            systemVoltage = obj.optInt("systemVoltage", 400),
                            systemFrequency = obj.optInt("systemFrequency", 50),
                            numberOfSources = obj.optInt("numberOfSources", 2),
                            atsLocation = obj.optString("atsLocation", "After Main Device"),
                            transferType = obj.optString("transferType", "Open Transition"),
                            utilizationCategory = obj.optString("utilizationCategory", "AC-31A (Resistive)"),
                            atsClass = obj.optString("atsClass", "PC (Transfer Switch)"),
                            loadCoverage = obj.optString("loadCoverage", "Emergency Only"),
                            ambientTemp = obj.optInt("ambientTemp", 45),
                            designSafetyFactor = obj.optDouble("designSafetyFactor", 1.25),
                            futureGrowthFactor = obj.optDouble("futureGrowthFactor", 10.0),
                            approvedManufacturer = obj.optString("approvedManufacturer", "ABB"),
                            mainsFailureDetectionDelay = obj.optInt("mainsFailureDetectionDelay", 3),
                            generatorStartSignalDelay = obj.optInt("generatorStartSignalDelay", 0),
                            generatorWarmUp = obj.optInt("generatorWarmUp", 7),
                            mainsReturnDetectionDelay = obj.optInt("mainsReturnDetectionDelay", 60),
                            reTransferDelay = obj.optInt("reTransferDelay", 10),
                            generatorCooldown = obj.optInt("generatorCooldown", 300),
                            enclosureIpRating = obj.optString("enclosureIpRating", "IP54"),
                            normalSourceId = obj.optString("normalSourceId").takeIf { !it.isNullOrBlank() },
                            emergencySourceId = obj.optString("emergencySourceId").takeIf { !it.isNullOrBlank() },
                            outputBoardId = remappedOutputBoard,
                            enableManualOverride = obj.optBoolean("enableManualOverride", false),
                            manualSizeAmp = if (obj.has("manualSizeAmp") && !obj.isNull("manualSizeAmp") && obj.optDouble("manualSizeAmp") > 0) obj.optDouble("manualSizeAmp") else null
                        )
                        powerSystemsDao.saveAtsSettings(ats)
                        importedAtsCount++
                    }
                } else if (rawAtsSingle != null) {
                    val rawOutputBoard = if (rawAtsSingle.has("outputBoardId") && !rawAtsSingle.isNull("outputBoardId") && rawAtsSingle.optInt("outputBoardId") != -1) rawAtsSingle.getInt("outputBoardId") else null
                    val remappedOutputBoard = if (rawOutputBoard != null && circuitIdMap.containsKey(rawOutputBoard)) circuitIdMap[rawOutputBoard] else null
                    val ats = AtsEntity(
                        projectId = newProjectId,
                        id = 1,
                        atsId = "ATS-01",
                        atsName = "ATS 1",
                        systemVoltage = rawAtsSingle.optInt("systemVoltage", 400),
                        systemFrequency = rawAtsSingle.optInt("systemFrequency", 50),
                        numberOfSources = rawAtsSingle.optInt("numberOfSources", 2),
                        atsLocation = rawAtsSingle.optString("atsLocation", "After Main Device"),
                        transferType = rawAtsSingle.optString("transferType", "Open Transition"),
                        utilizationCategory = rawAtsSingle.optString("utilizationCategory", "AC-31A (Resistive)"),
                        atsClass = rawAtsSingle.optString("atsClass", "PC (Transfer Switch)"),
                        loadCoverage = rawAtsSingle.optString("loadCoverage", "Emergency Only"),
                        ambientTemp = rawAtsSingle.optInt("ambientTemp", 45),
                        designSafetyFactor = rawAtsSingle.optDouble("designSafetyFactor", 1.25),
                        futureGrowthFactor = rawAtsSingle.optDouble("futureGrowthFactor", 10.0),
                        approvedManufacturer = rawAtsSingle.optString("approvedManufacturer", "ABB"),
                        mainsFailureDetectionDelay = rawAtsSingle.optInt("mainsFailureDetectionDelay", 3),
                        generatorStartSignalDelay = rawAtsSingle.optInt("generatorStartSignalDelay", 0),
                        generatorWarmUp = rawAtsSingle.optInt("generatorWarmUp", 7),
                        mainsReturnDetectionDelay = rawAtsSingle.optInt("mainsReturnDetectionDelay", 60),
                        reTransferDelay = rawAtsSingle.optInt("reTransferDelay", 10),
                        generatorCooldown = rawAtsSingle.optInt("generatorCooldown", 300),
                        enclosureIpRating = rawAtsSingle.optString("enclosureIpRating", "IP54"),
                        normalSourceId = rawAtsSingle.optString("normalSourceId").takeIf { !it.isNullOrBlank() },
                        emergencySourceId = rawAtsSingle.optString("emergencySourceId").takeIf { !it.isNullOrBlank() },
                        outputBoardId = remappedOutputBoard
                    )
                    powerSystemsDao.saveAtsSettings(ats)
                    importedAtsCount++
                }

                // 6. UPS
                val rawUpsList = if (root.has("ups")) root.getJSONArray("ups") else null
                val rawUpsSingle = if (rawUpsList == null && root.has("upsSettings")) root.getJSONObject("upsSettings") else null

                if (rawUpsList != null) {
                    for (i in 0 until rawUpsList.length()) {
                        val obj = rawUpsList.getJSONObject(i)
                        val idNum = obj.optInt("id", i + 1)
                        val ups = UpsEntity(
                            projectId = newProjectId,
                            id = idNum,
                            upsId = obj.optString("upsId", "UPS-%02d".format(idNum)),
                            upsName = obj.optString("upsName", "UPS $idNum"),
                            systemVoltage = obj.optInt("systemVoltage", 400),
                            systemFrequency = obj.optInt("systemFrequency", 50),
                            upsPf = obj.optDouble("upsPf", 0.9),
                            upsEfficiency = obj.optDouble("upsEfficiency", 94.0),
                            batteryType = obj.optString("batteryType", "VRLA"),
                            autonomyMinutes = obj.optInt("autonomyMinutes", 15),
                            batteryEodVoltage = obj.optDouble("batteryEodVoltage", 1.75),
                            ambientTemp = obj.optInt("ambientTemp", 25),
                            tempDeratingFactor = obj.optDouble("tempDeratingFactor", 1.0),
                            agingFactor = obj.optDouble("agingFactor", 1.25),
                            designMarginPct = obj.optDouble("designMarginPct", 10.0),
                            enableManualOverride = obj.optBoolean("enableManualOverride", false),
                            manualSizeKVA = if (obj.has("manualSizeKVA") && !obj.isNull("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
                        )
                        powerSystemsDao.saveUpsSettings(ups)
                        importedUpsCount++
                    }
                } else if (rawUpsSingle != null) {
                    val ups = UpsEntity(
                        projectId = newProjectId,
                        id = 1,
                        upsId = "UPS-01",
                        upsName = "UPS 1",
                        systemVoltage = rawUpsSingle.optInt("systemVoltage", 400),
                        systemFrequency = rawUpsSingle.optInt("systemFrequency", 50),
                        upsPf = rawUpsSingle.optDouble("upsPf", 0.9),
                        upsEfficiency = rawUpsSingle.optDouble("upsEfficiency", 94.0),
                        batteryType = rawUpsSingle.optString("batteryType", "VRLA"),
                        autonomyMinutes = rawUpsSingle.optInt("autonomyMinutes", 15),
                        batteryEodVoltage = rawUpsSingle.optDouble("batteryEodVoltage", 1.75),
                        ambientTemp = rawUpsSingle.optInt("ambientTemp", 25),
                        tempDeratingFactor = rawUpsSingle.optDouble("tempDeratingFactor", 1.0),
                        agingFactor = rawUpsSingle.optDouble("agingFactor", 1.25),
                        designMarginPct = rawUpsSingle.optDouble("designMarginPct", 10.0),
                        enableManualOverride = rawUpsSingle.optBoolean("enableManualOverride", false),
                        manualSizeKVA = if (rawUpsSingle.has("manualSizeKVA") && !rawUpsSingle.isNull("manualSizeKVA") && rawUpsSingle.optDouble("manualSizeKVA") > 0) rawUpsSingle.optDouble("manualSizeKVA") else null
                    )
                    powerSystemsDao.saveUpsSettings(ups)
                    importedUpsCount++
                }

                // 7. PF Global
                if (root.has("pfGlobal")) {
                    val obj = root.getJSONObject("pfGlobal")
                    val pfGlobal = PfGlobalEntity(
                        projectId = newProjectId,
                        id = 1,
                        targetPf = obj.optDouble("targetPf", 0.95),
                        kvarStepSize = obj.optDouble("kvarStepSize", 25.0),
                        systemVoltage = obj.optInt("systemVoltage", 400),
                        annualOperatingHours = obj.optInt("annualOperatingHours", 8760),
                        energyCost = obj.optDouble("energyCost", 0.12),
                        lossReductionPct = obj.optDouble("lossReductionPct", 15.0),
                        unityToleranceKvar = obj.optDouble("unityToleranceKvar", 5.0)
                    )
                    powerSystemsDao.savePfGlobalSettings(pfGlobal)
                    importedPfGlobalCount++
                }

                // 8. PF Transformers
                if (root.has("pfTransformers")) {
                    val arr = root.getJSONArray("pfTransformers")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val pft = PfTransformerEntity(
                            projectId = newProjectId,
                            transformerId = obj.optString("transformerId", "TX-${i + 1}"),
                            harmonicFilter = obj.optString("harmonicFilter", "None"),
                            detuningReactor = obj.optString("detuningReactor", "None")
                        )
                        powerSystemsDao.savePfTransformerSettings(pft)
                        importedPfTxCount++
                    }
                }

                // 9. Conduit Settings
                if (root.has("conduitSettings")) {
                    val arr = root.getJSONArray("conduitSettings")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val rawCircuitId = obj.optInt("circuitId", -1)
                        if (rawCircuitId != -1 && circuitIdMap.containsKey(rawCircuitId)) {
                            val newCircId = circuitIdMap[rawCircuitId]!!
                            val cs = ConduitSettingsEntity(
                                projectId = newProjectId,
                                circuitId = newCircId,
                                routingMode = obj.optString("routingMode", "ONE_PER_CONDUIT"),
                                designProfile = obj.optString("designProfile", "PROJECT_DESIGN"),
                                customFillLimitPct = obj.optDouble("customFillLimitPct", 40.0),
                                isDirectBuried = obj.optBoolean("isDirectBuried", false),
                                cpcArrangement = obj.optString("cpcArrangement", "INTERNAL_CORE"),
                                manualConduitOverride = obj.optString("manualConduitOverride").takeIf { !it.isNullOrBlank() },
                                manualCpcOD = if (obj.has("manualCpcOD") && !obj.isNull("manualCpcOD")) obj.optDouble("manualCpcOD") else null
                            )
                            conduitDao.saveConduitSetting(cs)
                            importedConduitCount++
                        } else if (version == 2) {
                            throw IllegalArgumentException("Unresolved conduit circuitId $rawCircuitId. Integrity violation.")
                        } else {
                            notices.add(MigrationNotice("Conduit", "Circuit $rawCircuitId", "circuitId", MigrationFieldClassification.UNAVAILABLE_FROM_V1, "Unresolved conduit setting for circuit $rawCircuitId dropped."))
                        }
                    }
                }

                // 10. Earthing Settings
                if (root.has("earthingSettings")) {
                    val obj = root.getJSONObject("earthingSettings")
                    val earthing = EarthingEntity(
                        projectId = newProjectId,
                        id = 1,
                        soilResistivity_ohmM = obj.optDouble("soilResistivity_ohmM", 100.0),
                        rodLength_m = obj.optDouble("rodLength_m", 3.0),
                        rodDiameter_mm = obj.optDouble("rodDiameter_mm", 16.0),
                        rodSpacing_m = obj.optDouble("rodSpacing_m", 6.0),
                        ringConductor_mm2 = obj.optDouble("ringConductor_mm2", 70.0),
                        ringBurialDepth_m = obj.optDouble("ringBurialDepth_m", 0.8),
                        kFactor = obj.optDouble("kFactor", 1.0),
                        projectType = obj.optString("projectType", "Commercial"),
                        disconnectionTime_s = obj.optDouble("disconnectionTime_s", 0.4),
                        targetResistanceOverride_ohm = if (obj.has("targetResistanceOverride_ohm") && !obj.isNull("targetResistanceOverride_ohm")) obj.optDouble("targetResistanceOverride_ohm") else null,
                        soilResistivitySource = obj.optString("soilResistivitySource", "DEFAULT_TABLE"),
                        designMarginPct = obj.optDouble("designMarginPct", 20.0),
                        isAutoDesignEnabled = obj.optBoolean("isAutoDesignEnabled", false),
                        siteLength_m = if (obj.has("siteLength_m") && !obj.isNull("siteLength_m")) obj.optDouble("siteLength_m") else null,
                        siteWidth_m = if (obj.has("siteWidth_m") && !obj.isNull("siteWidth_m")) obj.optDouble("siteWidth_m") else null,
                        autoDesignAppliedConfigId = obj.optString("autoDesignAppliedConfigId").takeIf { !it.isNullOrBlank() }
                    )
                    powerSystemsDao.saveEarthingSettings(earthing)
                    importedEarthingCount++
                }

                // 11. SLD Node Positions
                if (root.has("sldNodePositions")) {
                    val arr = root.getJSONArray("sldNodePositions")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val rawNodeId = obj.optString("nodeId", "")
                        val x = obj.optDouble("x", 0.0).toFloat()
                        val y = obj.optDouble("y", 0.0).toFloat()

                        if (rawNodeId.startsWith("circ_")) {
                            val oldCircId = rawNodeId.removePrefix("circ_").toIntOrNull()
                            if (oldCircId != null && circuitIdMap.containsKey(oldCircId)) {
                                val remappedNodeId = "circ_${circuitIdMap[oldCircId]}"
                                sldNodePositionDao.insertOrUpdatePosition(
                                    SldNodePositionEntity(
                                        projectId = newProjectId,
                                        nodeId = remappedNodeId,
                                        x = x,
                                        y = y
                                    )
                                )
                                importedSldCount++
                            } else if (version == 2) {
                                throw IllegalArgumentException("Unresolved SLD circuit node ID '$rawNodeId'. Integrity violation.")
                            } else {
                                notices.add(MigrationNotice("SLD", rawNodeId, "nodeId", MigrationFieldClassification.UNAVAILABLE_FROM_V1, "Unresolved SLD node $rawNodeId dropped."))
                            }
                        } else if (rawNodeId.isNotBlank()) {
                            sldNodePositionDao.insertOrUpdatePosition(
                                SldNodePositionEntity(
                                    projectId = newProjectId,
                                    nodeId = rawNodeId,
                                    x = x,
                                    y = y
                                )
                            )
                            importedSldCount++
                        }
                    }
                }
            }

            val counts = ImportCounts(
                circuits = importedCircuitCount,
                transformers = importedTxCount,
                generators = importedGenCount,
                ats = importedAtsCount,
                ups = importedUpsCount,
                pfGlobal = importedPfGlobalCount,
                pfTransformers = importedPfTxCount,
                conduitSettings = importedConduitCount,
                earthingSettings = importedEarthingCount,
                sldNodePositions = importedSldCount
            )

            val status = if (warnings.isNotEmpty()) ImportStatus.SUCCESS_WITH_WARNINGS else ImportStatus.SUCCESS

            ProjectImportResult(
                projectId = newProjectId,
                status = status,
                schemaVersion = version,
                importedCounts = counts,
                notices = notices,
                warnings = warnings,
                errors = emptyList(),
                hierarchyStatus = ValidationStatus.VALID,
                sldStatus = ValidationStatus.VALID,
                engineeringStatus = ValidationStatus.VALID
            )
        } catch (e: Exception) {
            ProjectImportResult(
                projectId = null,
                status = ImportStatus.FAILED,
                schemaVersion = 0,
                importedCounts = ImportCounts(),
                notices = emptyList(),
                warnings = emptyList(),
                errors = listOf(e.message ?: "Unknown import error occurred"),
                hierarchyStatus = ValidationStatus.INVALID,
                sldStatus = ValidationStatus.INVALID,
                engineeringStatus = ValidationStatus.INVALID
            )
        }
    }

    suspend fun importProjectFromJson(jsonString: String): String {
        val result = importProjectFromJsonDetailed(jsonString)
        if (result.status == ImportStatus.FAILED || result.projectId == null) {
            throw IllegalStateException(result.errors.firstOrNull() ?: "Failed to parse and import project JSON.")
        }
        return result.projectId
    }

    fun conduitSettingsFlow(projectId: String): Flow<List<ConduitSettingsEntity>> = db.conduitDao().getConduitSettingsForProject(projectId)
    suspend fun saveConduitSetting(setting: ConduitSettingsEntity) = withProjectMutation(setting.projectId) {
        db.conduitDao().saveConduitSetting(setting)
    }

    fun sldNodePositionsFlow(projectId: String): Flow<List<SldNodePositionEntity>> =
        sldNodePositionDao.getPositionsForProject(projectId)

    suspend fun saveSldNodePosition(position: SldNodePositionEntity) = withProjectMutation(position.projectId) {
        sldNodePositionDao.insertOrUpdatePosition(position)
    }

    suspend fun saveSldNodePositions(positions: List<SldNodePositionEntity>) {
        if (positions.isEmpty()) return
        val projectIds = positions.map { it.projectId }.distinct()
        if (projectIds.size == 1) {
            withProjectMutation(projectIds.first()) {
                sldNodePositionDao.insertOrUpdatePositions(positions)
            }
        } else {
            db.withTransaction {
                sldNodePositionDao.insertOrUpdatePositions(positions)
                projectIds.forEach { pid ->
                    projectDao.incrementRevision(pid, System.currentTimeMillis())
                }
            }
        }
    }

    suspend fun clearSldNodePositionsForProject(projectId: String) = withProjectMutation(projectId) {
        sldNodePositionDao.clearPositionsForProject(projectId)
    }
}


