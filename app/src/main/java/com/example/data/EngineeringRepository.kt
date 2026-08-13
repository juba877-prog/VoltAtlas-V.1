package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.io.InputStream

class EngineeringRepository(
    private val db: AppDatabase,

    val circuitDao: CircuitDao,
    val transformerDao: TransformerDao
) {
    val circuitsFlow: Flow<List<CircuitEntity>> = circuitDao.getAllCircuits()
    val transformersFlow: Flow<List<TransformerEntity>> = transformerDao.getAllTransformers()


    // Power Systems
    private val powerSystemsDao = db.powerSystemsDao()
    
    fun generatorSettingsFlow(projectId: String) = powerSystemsDao.getGeneratorSettings(projectId)
    fun generatorsFlow(projectId: String) = powerSystemsDao.getAllGeneratorSettings(projectId)
    suspend fun saveGeneratorSettings(settings: GeneratorEntity) = powerSystemsDao.saveGeneratorSettings(settings)
    suspend fun deleteGenerator(projectId: String, generatorId: String) {
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

    suspend fun createGeneratorAtomic(projectId: String, baseSettings: GeneratorEntity = GeneratorEntity()): GeneratorEntity {
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
        return newEntity
    }
    
    fun atsSettingsFlow(projectId: String) = powerSystemsDao.getAtsSettings(projectId)
    fun atsListFlow(projectId: String) = powerSystemsDao.getAllAtsSettings(projectId)
    suspend fun saveAtsSettings(settings: AtsEntity) = powerSystemsDao.saveAtsSettings(settings)
    suspend fun deleteAts(projectId: String, atsId: String) {
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        circuits.filter { it.assignedAtsId == atsId }.forEach { c ->
            circuitDao.insertCircuit(c.copy(assignedAtsId = null))
        }
        powerSystemsDao.deleteAtsSettings(projectId, atsId)
    }

    suspend fun createAtsAtomic(projectId: String, baseSettings: AtsEntity = AtsEntity()): AtsEntity {
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
        return newEntity
    }
    
    fun upsSettingsFlow(projectId: String) = powerSystemsDao.getUpsSettings(projectId)
    fun upsListFlow(projectId: String) = powerSystemsDao.getAllUpsSettings(projectId)
    suspend fun saveUpsSettings(settings: UpsEntity) = powerSystemsDao.saveUpsSettings(settings)
    suspend fun deleteUps(projectId: String, upsId: String) {
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        circuits.filter { it.assignedUpsId == upsId }.forEach { c ->
            circuitDao.insertCircuit(c.copy(assignedUpsId = null))
        }
        powerSystemsDao.deleteUpsSettings(projectId, upsId)
    }

    suspend fun createUpsAtomic(projectId: String, baseSettings: UpsEntity = UpsEntity()): UpsEntity {
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
        return newEntity
    }
    
    fun pfGlobalSettingsFlow(projectId: String) = powerSystemsDao.getPfGlobalSettings(projectId)
    suspend fun savePfGlobalSettings(settings: PfGlobalEntity) = powerSystemsDao.savePfGlobalSettings(settings)
    
    fun pfTransformerSettingsFlow(projectId: String) = powerSystemsDao.getAllPfTransformerSettings(projectId)
    suspend fun savePfTransformerSettings(settings: PfTransformerEntity) = powerSystemsDao.savePfTransformerSettings(settings)

    fun earthingSettingsFlow(projectId: String) = powerSystemsDao.getEarthingSettings(projectId)
    suspend fun saveEarthingSettings(settings: EarthingEntity) = powerSystemsDao.saveEarthingSettings(settings)

    suspend fun getAllCircuits(): List<CircuitEntity> = circuitDao.getAllCircuitsList()
    suspend fun getAllTransformers(): List<TransformerEntity> = transformerDao.getAllTransformersList()

    suspend fun getCircuitById(id: Int): CircuitEntity? = circuitDao.getCircuitById(id)
    suspend fun getCircuitByName(name: String): CircuitEntity? = circuitDao.getCircuitByName(name)
    suspend fun getTransformerById(projectId: String, id: String): TransformerEntity? = transformerDao.getTransformerById(projectId, id)

    suspend fun insertCircuit(circuit: CircuitEntity) = circuitDao.insertCircuit(circuit)
    suspend fun insertCircuits(circuits: List<CircuitEntity>) = circuitDao.insertCircuits(circuits)
    suspend fun updateCircuit(circuit: CircuitEntity) = circuitDao.updateCircuit(circuit)
    suspend fun deleteCircuit(circuit: CircuitEntity) = circuitDao.deleteCircuit(circuit)
    suspend fun deleteAllCircuits() = circuitDao.deleteAllCircuits()
    suspend fun deleteCircuitsForProject(projectId: String) = circuitDao.deleteCircuitsForProject(projectId)

    suspend fun insertTransformer(transformer: TransformerEntity) = transformerDao.insertTransformer(transformer)
    suspend fun insertTransformers(transformers: List<TransformerEntity>) = transformerDao.insertTransformers(transformers)
    suspend fun updateTransformer(transformer: TransformerEntity) = transformerDao.updateTransformer(transformer)
    suspend fun deleteTransformer(transformer: TransformerEntity) = transformerDao.deleteTransformer(transformer)
    suspend fun deleteAllTransformers() = transformerDao.deleteAllTransformers()
    suspend fun deleteTransformersForProject(projectId: String) = transformerDao.deleteTransformersForProject(projectId)


    // Project management
    val projectDao = db.projectDao()
    val appStateDao = db.appStateDao()

    fun getAllProjects() = projectDao.getAllProjects()
    fun observeProject(id: String) = projectDao.observeProject(id)

    suspend fun updateProjectImbalanceLimit(projectId: String, limitPct: Double) {
        val proj = projectDao.getProject(projectId) ?: return
        val updated = proj.copy(
            defaultPhaseImbalanceLimitPct = limitPct,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(updated)
    }

    suspend fun updateCircuitImbalanceOverride(circuitId: Int, limitOverridePct: Double?) {
        val circuit = circuitDao.getCircuitById(circuitId) ?: return
        val updated = circuit.copy(phaseImbalanceLimitOverridePct = limitOverridePct)
        circuitDao.insertCircuit(updated)
    }

    suspend fun createProject(name: String, client: String? = null): String {
        val id = "PRJ_" + System.currentTimeMillis()
        val now = System.currentTimeMillis()
        projectDao.upsertProject(
            ProjectEntity(projectId = id, projectName = name, clientName = client,
                          createdAt = now, updatedAt = now)
        )
        return id
    }

    suspend fun renameProject(id: String, name: String) =
        projectDao.rename(id, name, System.currentTimeMillis())

    @androidx.room.Transaction
    suspend fun deleteProject(id: String) {
        circuitDao.deleteCircuitsForProject(id)
        transformerDao.deleteTransformersForProject(id)
        projectDao.deleteProject(id)
        if (appStateDao.getActiveProjectId() == id)
            appStateDao.setState(AppStateEntity(0, null))
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

    // Generic JSON Export and Import for any user project
    suspend fun exportProjectJson(projectId: String): String {
        val proj = projectDao.getProject(projectId) ?: return "{}"
        val circuits = circuitDao.getCircuitsListForProject(projectId)
        val transformers = transformerDao.getTransformersListForProject(projectId)


        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("appName", "VoltAtlas")

        val projObj = org.json.JSONObject()
        projObj.put("projectName", proj.projectName)
        projObj.put("projectCode", proj.projectCode ?: "")
        projObj.put("clientName", proj.clientName ?: "")
        projObj.put("defaultPhaseImbalanceLimitPct", proj.defaultPhaseImbalanceLimitPct)
        root.put("project", projObj)

        val circuitsArr = org.json.JSONArray()
        circuits.forEach { c ->
            val obj = org.json.JSONObject()
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
            obj.put("length_m", c.length_m)
            obj.put("overrideDeviceType", c.overrideDeviceType ?: "")
            obj.put("overrideDeviceSize", c.overrideDeviceSize ?: 0.0)
            obj.put("overrideCableSize", c.overrideCableSize ?: "")
            obj.put("demandFactor", c.demandFactor)
            obj.put("diversityGroup", c.diversityGroup ?: "")
            obj.put("generatorIncluded", c.generatorIncluded)
            obj.put("atsIncluded", c.atsIncluded)
            obj.put("upsBacked", c.upsBacked)
            obj.put("assignedGeneratorId", c.assignedGeneratorId ?: "")
            obj.put("assignedAtsId", c.assignedAtsId ?: "")
            obj.put("assignedUpsId", c.assignedUpsId ?: "")
            obj.put("loadNature", c.loadNature)
            obj.put("startingMethod", c.startingMethod ?: "")
            obj.put("startingCurrentMultiplier", c.startingCurrentMultiplier ?: 1.0)
            obj.put("panelType", c.panelType ?: "")
            obj.put("parentId", c.parentId ?: -1)
            obj.put("sourceType", c.sourceType ?: "")
            if (!c.loadCategory.isNullOrBlank()) {
                obj.put("loadCategory", c.loadCategory)
            }
            if (c.phaseImbalanceLimitOverridePct != null) {
                obj.put("phaseImbalanceLimitOverridePct", c.phaseImbalanceLimitOverridePct)
            }
            if (c.busbarRatingA != null) obj.put("busbarRatingA", c.busbarRatingA)
            if (c.scRatingKA != null) obj.put("scRatingKA", c.scRatingKA)
            if (!c.mainIncomingType.isNullOrBlank()) obj.put("mainIncomingType", c.mainIncomingType)
            if (c.mainIncomingCount != null) obj.put("mainIncomingCount", c.mainIncomingCount)
            if (!c.ipRating.isNullOrBlank()) obj.put("ipRating", c.ipRating)
            if (!c.panelLocation.isNullOrBlank()) obj.put("panelLocation", c.panelLocation)
            if (!c.cableEntry.isNullOrBlank()) obj.put("cableEntry", c.cableEntry)
            if (!c.feederDescription.isNullOrBlank()) obj.put("feederDescription", c.feederDescription)
            if (c.numberOfWays != null) obj.put("numberOfWays", c.numberOfWays)
            circuitsArr.put(obj)
        }
        root.put("circuits", circuitsArr)

        val transformersArr = org.json.JSONArray()
        transformers.forEach { t ->
            val obj = org.json.JSONObject()
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
            transformersArr.put(obj)
        }
        root.put("transformers", transformersArr)

        // Add Multi-Instance Generators
        val generatorsList = powerSystemsDao.getAllGeneratorSettings(projectId).firstOrNull() ?: emptyList()
        val genArr = org.json.JSONArray()
        generatorsList.forEach { gen ->
            val genObj = org.json.JSONObject()
            genObj.put("id", gen.id)
            genObj.put("generatorId", gen.generatorId)
            genObj.put("generatorName", gen.generatorName)
            genObj.put("additionalEssentialLoadKW", gen.additionalEssentialLoadKW)
            genObj.put("largestMotorKW", gen.largestMotorKW)
            genObj.put("motorStartMethod", gen.motorStartMethod)
            genObj.put("motorPf", gen.motorPf)
            genObj.put("generatorPf", gen.generatorPf)
            genObj.put("demandFactor", gen.demandFactor)
            genObj.put("diversityFactor", gen.diversityFactor)
            genObj.put("stepLoadPct", gen.stepLoadPct)
            genObj.put("oversizingFactor", gen.oversizingFactor)
            genObj.put("enableManualOverride", gen.enableManualOverride)
            genObj.put("manualSizeKVA", gen.manualSizeKVA ?: 0.0)
            genArr.put(genObj)
        }
        root.put("generators", genArr)
        if (generatorsList.isNotEmpty()) {
            root.put("generatorSettings", genArr.getJSONObject(0))
        }

        // Add Multi-Instance ATS
        val atsList = powerSystemsDao.getAllAtsSettings(projectId).firstOrNull() ?: emptyList()
        val atsArr = org.json.JSONArray()
        atsList.forEach { ats ->
            val atsObj = org.json.JSONObject()
            atsObj.put("id", ats.id)
            atsObj.put("atsId", ats.atsId)
            atsObj.put("atsName", ats.atsName)
            atsObj.put("systemVoltage", ats.systemVoltage)
            atsObj.put("systemFrequency", ats.systemFrequency)
            atsObj.put("numberOfSources", ats.numberOfSources)
            atsObj.put("atsLocation", ats.atsLocation)
            atsObj.put("transferType", ats.transferType)
            atsObj.put("utilizationCategory", ats.utilizationCategory)
            atsObj.put("atsClass", ats.atsClass)
            atsObj.put("loadCoverage", ats.loadCoverage)
            atsObj.put("ambientTemp", ats.ambientTemp)
            atsObj.put("designSafetyFactor", ats.designSafetyFactor)
            atsObj.put("futureGrowthFactor", ats.futureGrowthFactor)
            atsObj.put("approvedManufacturer", ats.approvedManufacturer)
            atsObj.put("mainsFailureDetectionDelay", ats.mainsFailureDetectionDelay)
            atsObj.put("generatorStartSignalDelay", ats.generatorStartSignalDelay)
            atsObj.put("generatorWarmUp", ats.generatorWarmUp)
            atsObj.put("mainsReturnDetectionDelay", ats.mainsReturnDetectionDelay)
            atsObj.put("reTransferDelay", ats.reTransferDelay)
            atsObj.put("generatorCooldown", ats.generatorCooldown)
            atsObj.put("enclosureIpRating", ats.enclosureIpRating)
            atsObj.put("normalSourceId", ats.normalSourceId ?: "")
            atsObj.put("emergencySourceId", ats.emergencySourceId ?: "")
            atsObj.put("outputBoardId", ats.outputBoardId ?: -1)
            atsObj.put("enableManualOverride", ats.enableManualOverride)
            atsObj.put("manualSizeAmp", ats.manualSizeAmp ?: 0.0)
            atsArr.put(atsObj)
        }
        root.put("ats", atsArr)
        if (atsList.isNotEmpty()) {
            root.put("atsSettings", atsArr.getJSONObject(0))
        }

        // Add Multi-Instance UPS
        val upsList = powerSystemsDao.getAllUpsSettings(projectId).firstOrNull() ?: emptyList()
        val upsArr = org.json.JSONArray()
        upsList.forEach { ups ->
            val upsObj = org.json.JSONObject()
            upsObj.put("id", ups.id)
            upsObj.put("upsId", ups.upsId)
            upsObj.put("upsName", ups.upsName)
            upsObj.put("systemVoltage", ups.systemVoltage)
            upsObj.put("systemFrequency", ups.systemFrequency)
            upsObj.put("upsPf", ups.upsPf)
            upsObj.put("upsEfficiency", ups.upsEfficiency)
            upsObj.put("batteryType", ups.batteryType)
            upsObj.put("autonomyMinutes", ups.autonomyMinutes)
            upsObj.put("enableManualOverride", ups.enableManualOverride)
            upsObj.put("manualSizeKVA", ups.manualSizeKVA ?: 0.0)
            upsArr.put(upsObj)
        }
        root.put("ups", upsArr)
        if (upsList.isNotEmpty()) {
            root.put("upsSettings", upsArr.getJSONObject(0))
        }

        return root.toString(2)
    }

    suspend fun importProjectFromJson(jsonString: String): String {
        val root = org.json.JSONObject(jsonString)
        val projObj = root.optJSONObject("project")
        val name = projObj?.optString("projectName")?.takeIf { it.isNotBlank() } ?: "Imported Project"
        val client = projObj?.optString("clientName")?.takeIf { it.isNotBlank() }

        val newId = createProject(name, client)
        if (projObj != null && projObj.has("defaultPhaseImbalanceLimitPct")) {
            val limitPct = projObj.optDouble("defaultPhaseImbalanceLimitPct", 20.0)
            updateProjectImbalanceLimit(newId, limitPct)
        }

        if (root.has("transformers")) {
            val arr = root.getJSONArray("transformers")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tx = TransformerEntity(
                    transformerId = obj.optString("transformerId", "TX-${i + 1}"),
                    projectId = newId,
                    transformerName = obj.optString("transformerName", "Transformer ${i + 1}"),
                    kVA = obj.optDouble("kVA", 1000.0),
                    voltage = obj.optInt("voltage", 400),
                    percentZ = obj.optDouble("percentZ", 5.0),
                    xr = obj.optDouble("xr", 7.0),
                    txType = obj.optString("txType", "Cast Resin"),
                    sourceType = obj.optString("sourceType", "Medium Voltage"),
                    status = obj.optString("status", "Active"),
                    sizeMode = obj.optString("sizeMode", "Auto"),
                    manualKVA = obj.optDouble("manualKVA", 1000.0),
                    demandFactor = obj.optDouble("demandFactor", 0.8),
                    diversity = obj.optDouble("diversity", 1.0),
                    growth = obj.optDouble("growth", 1.0),
                    safety = obj.optDouble("safety", 1.0)
                )
                transformerDao.insertTransformer(tx)
            }
        }

        if (root.has("circuits")) {
            val arr = root.getJSONArray("circuits")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val c = CircuitEntity(
                    circuitName = obj.optString("circuitName", "Circuit-${i + 1}"),
                    projectId = newId,
                    loadKVA = obj.optDouble("loadKVA", 10.0),
                    fedFrom = obj.optString("fedFrom", "Transformer"),
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
                    overrideDeviceSize = if (obj.has("overrideDeviceSize") && obj.optDouble("overrideDeviceSize") > 0) obj.optDouble("overrideDeviceSize") else null,
                    overrideCableSize = obj.optString("overrideCableSize").takeIf { !it.isNullOrBlank() },
                    demandFactor = obj.optDouble("demandFactor", 1.0),
                    diversityGroup = obj.optString("diversityGroup").takeIf { !it.isNullOrBlank() },
                    generatorIncluded = obj.optBoolean("generatorIncluded", false),
                    atsIncluded = obj.optBoolean("atsIncluded", false),
                    upsBacked = obj.optBoolean("upsBacked", false),
                    assignedGeneratorId = obj.optString("assignedGeneratorId").takeIf { !it.isNullOrBlank() },
                    assignedAtsId = obj.optString("assignedAtsId").takeIf { !it.isNullOrBlank() },
                    assignedUpsId = obj.optString("assignedUpsId").takeIf { !it.isNullOrBlank() },
                    loadNature = obj.optString("loadNature", "Static"),
                    startingMethod = obj.optString("startingMethod").takeIf { !it.isNullOrBlank() },
                    startingCurrentMultiplier = if (obj.has("startingCurrentMultiplier")) obj.optDouble("startingCurrentMultiplier") else null,
                    panelType = obj.optString("panelType").takeIf { it.isNotBlank() },
                    parentId = if (obj.has("parentId") && obj.optInt("parentId") != -1) obj.optInt("parentId") else null,
                    sourceType = obj.optString("sourceType").takeIf { it.isNotBlank() },
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
                    numberOfWays = if (obj.has("numberOfWays") && !obj.isNull("numberOfWays")) obj.optInt("numberOfWays") else null
                )

                circuitDao.insertCircuit(c)
            }
        }

        if (root.has("generators")) {
            val arr = root.getJSONArray("generators")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idNum = obj.optInt("id", i + 1)
                val gen = GeneratorEntity(
                    projectId = newId,
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
                    manualSizeKVA = if (obj.has("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
                )
                powerSystemsDao.saveGeneratorSettings(gen)
            }
        } else if (root.has("generatorSettings")) {
            val obj = root.getJSONObject("generatorSettings")
            val gen = GeneratorEntity(
                projectId = newId,
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
                manualSizeKVA = if (obj.has("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
            )
            powerSystemsDao.saveGeneratorSettings(gen)
        }

        if (root.has("ats")) {
            val arr = root.getJSONArray("ats")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idNum = obj.optInt("id", i + 1)
                val ats = AtsEntity(
                    projectId = newId,
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
                    normalSourceId = obj.optString("normalSourceId").takeIf { it.isNotBlank() },
                    emergencySourceId = obj.optString("emergencySourceId").takeIf { it.isNotBlank() },
                    outputBoardId = if (obj.has("outputBoardId") && obj.optInt("outputBoardId") != -1) obj.optInt("outputBoardId") else null,
                    enableManualOverride = obj.optBoolean("enableManualOverride", false),
                    manualSizeAmp = if (obj.has("manualSizeAmp") && obj.optDouble("manualSizeAmp") > 0) obj.optDouble("manualSizeAmp") else null
                )
                powerSystemsDao.saveAtsSettings(ats)
            }
        } else if (root.has("atsSettings")) {
            val obj = root.getJSONObject("atsSettings")
            val ats = AtsEntity(
                projectId = newId,
                id = 1,
                atsId = "ATS-01",
                atsName = "ATS 1",
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
                normalSourceId = obj.optString("normalSourceId").takeIf { it.isNotBlank() },
                emergencySourceId = obj.optString("emergencySourceId").takeIf { it.isNotBlank() },
                outputBoardId = if (obj.has("outputBoardId") && obj.optInt("outputBoardId") != -1) obj.optInt("outputBoardId") else null
            )
            powerSystemsDao.saveAtsSettings(ats)
        }

        if (root.has("ups")) {
            val arr = root.getJSONArray("ups")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idNum = obj.optInt("id", i + 1)
                val ups = UpsEntity(
                    projectId = newId,
                    id = idNum,
                    upsId = obj.optString("upsId", "UPS-%02d".format(idNum)),
                    upsName = obj.optString("upsName", "UPS $idNum"),
                    systemVoltage = obj.optInt("systemVoltage", 400),
                    systemFrequency = obj.optInt("systemFrequency", 50),
                    upsPf = obj.optDouble("upsPf", 0.9),
                    upsEfficiency = obj.optDouble("upsEfficiency", 94.0),
                    batteryType = obj.optString("batteryType", "VRLA"),
                    autonomyMinutes = obj.optInt("autonomyMinutes", 15),
                    enableManualOverride = obj.optBoolean("enableManualOverride", false),
                    manualSizeKVA = if (obj.has("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
                )
                powerSystemsDao.saveUpsSettings(ups)
            }
        } else if (root.has("upsSettings")) {
            val obj = root.getJSONObject("upsSettings")
            val idNum = 1
            val ups = UpsEntity(
                projectId = newId,
                id = idNum,
                upsId = "UPS-01",
                upsName = "UPS 1",
                systemVoltage = obj.optInt("systemVoltage", 400),
                systemFrequency = obj.optInt("systemFrequency", 50),
                upsPf = obj.optDouble("upsPf", 0.9),
                upsEfficiency = obj.optDouble("upsEfficiency", 94.0),
                batteryType = obj.optString("batteryType", "VRLA"),
                autonomyMinutes = obj.optInt("autonomyMinutes", 15),
                enableManualOverride = obj.optBoolean("enableManualOverride", false),
                manualSizeKVA = if (obj.has("manualSizeKVA") && obj.optDouble("manualSizeKVA") > 0) obj.optDouble("manualSizeKVA") else null
            )
            powerSystemsDao.saveUpsSettings(ups)
        }

        return newId
    }

    fun conduitSettingsFlow(projectId: String): Flow<List<ConduitSettingsEntity>> = db.conduitDao().getConduitSettingsForProject(projectId)
    suspend fun saveConduitSetting(setting: ConduitSettingsEntity) = db.conduitDao().saveConduitSetting(setting)

}


