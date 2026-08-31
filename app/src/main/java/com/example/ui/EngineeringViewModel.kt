package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.engine.BoqEngine
import com.example.engine.CableSizingEngine
import com.example.engine.DrumScheduleEngine
import com.example.engine.DrumAllocation
import com.example.engine.CableCut
import com.example.engine.PanelScheduleEngine
import com.example.engine.ProcurementEngine
import com.example.engine.SizingResult
import com.example.engine.TransformerEngine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EngineeringViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = com.example.data.GlobalPreferences.getInstance(application)

    private val _showGlobalSettings = MutableStateFlow(false)
    val showGlobalSettings = _showGlobalSettings.asStateFlow()

    fun openGlobalSettings() {
        _showGlobalSettings.value = true
    }

    fun closeGlobalSettings() {
        _showGlobalSettings.value = false
    }

    private val db = AppDatabase.getDatabase(application)
    private val repository = EngineeringRepository(
        db,
        db.circuitDao(),
        db.transformerDao()
    )

    private val _activeProjectId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val activeProjectId: kotlinx.coroutines.flow.StateFlow<String?> = _activeProjectId

    val selectedTransformerId = MutableStateFlow("")
    val selectedCircuitId = MutableStateFlow<Int?>(null)

    val circuitSearchQuery = MutableStateFlow("")
    val circuitStatusFilter = MutableStateFlow("All")
    val circuitParentFilter = MutableStateFlow("All")

    /**
     * Explicit UI recalculation trigger.
     * It NEVER resets Room and NEVER reloads QA/reference overrides.
     */
    private val recalculationToken = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val generatorSettings = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.generatorSettingsFlow(projectId) else kotlinx.coroutines.flow.flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val generatorsList = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.generatorsFlow(projectId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val atsSettings = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.atsSettingsFlow(projectId) else kotlinx.coroutines.flow.flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val atsList = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.atsListFlow(projectId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val upsSettings = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.upsSettingsFlow(projectId) else kotlinx.coroutines.flow.flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val upsList = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.upsListFlow(projectId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pfGlobalSettings = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.pfGlobalSettingsFlow(projectId) else kotlinx.coroutines.flow.flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pfTransformerSettings = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.pfTransformerSettingsFlow(projectId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val earthingSettings = activeProjectId.flatMapLatest { projectId ->
        if (projectId != null) repository.earthingSettingsFlow(projectId) else kotlinx.coroutines.flow.flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)





    // Project Management
    val projects: kotlinx.coroutines.flow.StateFlow<List<com.example.data.ProjectEntity>> = repository.getAllProjects()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeProject: kotlinx.coroutines.flow.StateFlow<com.example.data.ProjectEntity?> = activeProjectId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(null)
        else repository.observeProject(id)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    fun updateActiveProjectImbalanceLimit(limitPct: Double) = viewModelScope.launch {
        val prjId = _activeProjectId.value ?: return@launch
        repository.updateProjectImbalanceLimit(prjId, limitPct)
    }

    fun updateCircuitImbalanceOverride(circuitId: Int, limitOverridePct: Double?) = viewModelScope.launch {
        repository.updateCircuitImbalanceOverride(circuitId, limitOverridePct)
    }

    fun loadInitialState() = viewModelScope.launch {
        // Force the ProjectPicker on startup
        _activeProjectId.value = null
    }

    fun createAndOpenProject(name: String) = viewModelScope.launch {
        val id = repository.createProject(name)
        repository.setActiveProject(id)
        preferences.setLastOpenedProjectId(id)
        selectedCircuitId.value = null
        selectedTransformerId.value = ""
        circuitSearchQuery.value = ""
        circuitStatusFilter.value = "All"
        circuitParentFilter.value = "All"
        _activeProjectId.value = id
        recalculate()
    }

    fun openProject(id: String) = viewModelScope.launch {
        repository.setActiveProject(id)
        preferences.setLastOpenedProjectId(id)
        selectedCircuitId.value = null
        selectedTransformerId.value = ""
        circuitSearchQuery.value = ""
        circuitStatusFilter.value = "All"
        circuitParentFilter.value = "All"
        _activeProjectId.value = id
        recalculate()
    }

    fun closeProject() = viewModelScope.launch {
        repository.setActiveProject(null)
        preferences.setLastOpenedProjectId(null)
        _activeProjectId.value = null
        selectedCircuitId.value = null
        selectedTransformerId.value = ""
        circuitSearchQuery.value = ""
        circuitStatusFilter.value = "All"
        circuitParentFilter.value = "All"
    }

    fun renameProject(id: String, newName: String) = viewModelScope.launch {
        repository.renameProject(id, newName)
    }

    fun deleteProject(id: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.deleteProject(id)
        }
        if (_activeProjectId.value == id) {
            _activeProjectId.value = null
            selectedCircuitId.value = null
            selectedTransformerId.value = ""
            circuitSearchQuery.value = ""
            circuitStatusFilter.value = "All"
            circuitParentFilter.value = "All"
            preferences.setLastOpenedProjectId(null)
        }
    }

    val projectError = MutableStateFlow<String?>(null)
    val startupState = MutableStateFlow<StartupState>(StartupState.Initial)

    /** R2a: true once the catalog load attempt has finished (success or failure). */
    val catalogReady = MutableStateFlow(false)
    private val _autoDesignResultState = MutableStateFlow<com.example.engine.EarthingAutoDesignResult?>(null)

    init {
        retryStartup()
    }

    fun retryStartup() {
        viewModelScope.launch {
            try {
                val warnings = mutableListOf<String>()

                // 1. Launch Catalog loading concurrently on Dispatchers.IO
                val catalogJob = launch(Dispatchers.IO) {
                    try {
                        Catalog.loadAll(getApplication())
                    } catch (e: Exception) {
                        synchronized(warnings) {
                            warnings.add("Failed to load catalogs: ${e.message}")
                        }
                    }
                }

                // 2. Perform Database opening & state restoration in parallel
                startupState.value = StartupState.OpeningDatabase
                val projects = withContext(Dispatchers.IO) {
                    repository.getAllProjectsList()
                }

                startupState.value = StartupState.LoadingProjects

                startupState.value = StartupState.RestoringApplicationState
                initializeNewInstallationIfRequired()

                // 3. Await catalogJob before emitting StartupState.Ready
                catalogJob.join()

                // R2a: the catalog load ATTEMPT has finished (success OR failure).
                // Unblocks the uiState combine guard. Never set back to false.
                catalogReady.value = true

                if (projects.isEmpty()) {
                    startupState.value = StartupState.NoProjects
                } else if (warnings.isNotEmpty()) {
                    startupState.value = StartupState.ReadyWithWarnings(warnings)
                } else {
                    startupState.value = StartupState.Ready
                }
            } catch (e: Exception) {
                startupState.value = StartupState.DatabaseError("VoltAtlas could not open the local project database.", e.message)
            }
        }
    }


    /**
     * On first application start, create only one default transformer when no
     * transformers exist. Existing circuits and overrides are never replaced.
     */
    val confirmDestructiveActions = preferences.confirmDestructiveActions
    val startupBehavior = preferences.startupBehavior
    val themeMode = preferences.themeMode
    val defaultFrequency = preferences.defaultFrequency
    val defaultVoltage3Phase = preferences.defaultVoltage3Phase
    val maxVdLimitLighting = preferences.maxVdLimitLighting
    val maxVdLimitPower = preferences.maxVdLimitPower
    val defaultConductorMaterial = preferences.defaultConductorMaterial
    val defaultInsulation = preferences.defaultInsulation

    private suspend fun initializeNewInstallationIfRequired() {
        try {
            _activeProjectId.value = null // Never auto-open project on startup

            preferences.awaitLoaded()

            val allProjects = repository.getAllProjectsList()
            val lastId = preferences.lastOpenedProjectId.value

            if (lastId != null) {
                if (allProjects.none { it.projectId == lastId }) {
                    preferences.setLastOpenedProjectId(null)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("VoltAtlas could not open the local project database. Existing data has not been deleted. (${e.message})", e)
        }
    }


    fun importProjectJson(jsonString: String, onComplete: (Boolean, String) -> Unit) = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                repository.importProjectFromJsonDetailed(jsonString)
            }
            if (result.status != com.example.data.ImportStatus.FAILED && result.projectId != null) {
                val newProjectId = result.projectId
                repository.setActiveProject(newProjectId)
                preferences.setLastOpenedProjectId(newProjectId)
                selectedCircuitId.value = null
                selectedTransformerId.value = ""
                circuitSearchQuery.value = ""
                circuitStatusFilter.value = "All"
                circuitParentFilter.value = "All"
                _activeProjectId.value = newProjectId
                recalculate()
                val message = if (result.warnings.isNotEmpty()) {
                    "Imported with ${result.warnings.size} warning(s)"
                } else {
                    "Project imported successfully"
                }
                onComplete(true, message)
            } else {
                onComplete(false, result.errors.firstOrNull() ?: "Failed to parse and import project JSON.")
            }
        } catch (e: Exception) {
            onComplete(false, e.message ?: "Failed to parse and import project JSON.")
        }
    }

    suspend fun exportProjectJson(projectId: String): String {
        return repository.exportProjectJson(projectId)
    }

    fun createDefaultTransformer(id: String): TransformerEntity =
        TransformerEntity(
            transformerId = id,
            transformerName = "Transformer $id",
            kVA = 1000.0,
            voltage = preferences.defaultVoltage3Phase.value,
            percentZ = 6.0,
            xr = 6.0,
            txType = "Dry Type (Cast Resin)",
            sourceType = "Medium Voltage",
            status = "Active",
            sizeMode = "Auto",
            manualKVA = 1000.0,
            demandFactor = 0.8,
            diversity = 1.0,
            growth = 1.0,
            safety = 1.0
        )

    fun createDefaultCircuit(
        name: String = "New Circuit",
        loadKVA: Double = 10.0,
        fedFrom: String = "Transformer",
        phases: String = "3P+N",
        loadType: String = "Normal"
    ): CircuitEntity {
        val v3p = preferences.defaultVoltage3Phase.value
        val voltage = if (phases == "3P+N" || phases == "3P") v3p else when (v3p) {
            380 -> 220
            400 -> 230
            415 -> 240
            480 -> 277
            else -> (v3p / 1.732).toInt()
        }
        val cond = preferences.defaultConductorMaterial.value
        val ins = preferences.defaultInsulation.value
        val maxVd = if (loadType.equals("Lighting", ignoreCase = true)) {
            preferences.maxVdLimitLighting.value / 100.0
        } else {
            preferences.maxVdLimitPower.value / 100.0
        }

        return CircuitEntity(
            id = 0,
            circuitName = name,
            loadKVA = loadKVA,
            fedFrom = fedFrom,
            transformerId = selectedTransformerId.value,
            loadType = loadType,
            phases = phases,
            voltageV = voltage,
            pf = 0.85,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = com.example.data.resolveCableType("$cond/$ins/PVC", preferences.defaultCoreConfig.value, cond, ins),
            installation = "Laid in Free Air",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 50.0,
            maxVDpct = maxVd
        )
    }





    data class PowerSettingsData(
        val gen: com.example.data.GeneratorEntity?, 
        val ats: com.example.data.AtsEntity?, 
        val ups: com.example.data.UpsEntity?, 
        val pfG: com.example.data.PfGlobalEntity?, 
        val pfT: List<com.example.data.PfTransformerEntity>,
        val earthing: com.example.data.EarthingEntity? = null
    )
    
    val powerSettingsFlow = combine(generatorSettings, atsSettings, upsSettings, pfGlobalSettings, pfTransformerSettings, earthingSettings) { array ->
        val gen = array[0] as? com.example.data.GeneratorEntity
        val ats = array[1] as? com.example.data.AtsEntity
        val ups = array[2] as? com.example.data.UpsEntity
        val pfG = array[3] as? com.example.data.PfGlobalEntity
        @Suppress("UNCHECKED_CAST")
        val pfT = (array[4] as? List<com.example.data.PfTransformerEntity>) ?: emptyList()
        val earth = array[5] as? com.example.data.EarthingEntity
        PowerSettingsData(gen, ats, ups, pfG, pfT, earth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PowerSettingsData(null, null, null, null, emptyList(), null))


    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val projectCircuitsFlow = activeProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.circuitsForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val projectTransformersFlow = activeProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.transformersForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun normalizeAutoOverride(circuit: CircuitEntity): CircuitEntity {
        val normalizedCableOverride = circuit.overrideCableSize
            ?.trim()
            ?.takeUnless { it.isEmpty() || it.equals("AUTO", ignoreCase = true) }
        return circuit.copy(overrideCableSize = normalizedCableOverride)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conduitSettingsFlow = activeProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.conduitSettingsFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveConduitSetting(setting: com.example.data.ConduitSettingsEntity) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.saveConduitSetting(setting.copy(projectId = prj))
        }
    }

    // --- PDF Reporting ---
    private val _pdfExportState = MutableStateFlow<String>("Idle")
    val pdfExportState = _pdfExportState.asStateFlow()

    fun setPdfExportState(s: String) { _pdfExportState.value = s }

    fun resetPdfExportState() {
        _pdfExportState.value = "Idle"
    }

    suspend fun exportPdf(uri: android.net.Uri, options: com.example.pdf.ReportOptions) {
        val currentState = uiState.value as? UiState.Success ?: return
        val activeId = _activeProjectId.value ?: return
        val project = projects.value.find { it.projectId == activeId } ?: return

        _pdfExportState.value = "BuildingSnapshot"
        
        val snapshot = com.example.pdf.ProjectReportSnapshot(
            project = project,
            circuits = currentState.circuits,
            transformers = currentState.transformers,
            sizingResults = currentState.sizingResults,
            shortCircuitResults = currentState.shortCircuitResults,
            conduitResults = currentState.conduitResults,
            conduitProcurement = currentState.conduitProcurement,
            adsResults = currentState.adsResults,
            transformerResults = currentState.transformerResults,
            boq = currentState.boq,
            procurement = currentState.procurement,
            drums = currentState.drums,
            generatorResult = currentState.generatorResult,
            atsResult = currentState.atsResult,
            upsResult = currentState.upsResult,
            pfCorrectionResults = currentState.pfCorrectionResults,
            earthingResult = currentState.earthingResult,
            options = options
        )
        
        _pdfExportState.value = "Rendering"
        
        try {
            val service = com.example.pdf.PdfReportService(getApplication())
            val bytesWritten = service.exportReport(snapshot) { getApplication<android.app.Application>().contentResolver.openOutputStream(uri) }
            if (bytesWritten > 0L) {
                _pdfExportState.value = "Success"
            } else {
                _pdfExportState.value = "Failed: Zero bytes exported"
            }
        } catch (e: Exception) {
            _pdfExportState.value = "Failed: ${e.message}"
        }
    }

    val uiState: StateFlow<UiState> = combine(
        projectCircuitsFlow,
        projectTransformersFlow,
        recalculationToken,
        powerSettingsFlow,
        conduitSettingsFlow,
        generatorsList,
        atsList,
        upsList,
        catalogReady
    ) { flows ->
        val catalogReadyFlag = flows[8] as Boolean
        // R2a guard: hold Loading until the catalog load attempt has finished.
        if (!catalogReadyFlag) return@combine UiState.Loading
        @Suppress("UNCHECKED_CAST")
        val circuits = flows[0] as List<CircuitEntity>
        @Suppress("UNCHECKED_CAST")
        val transformers = flows[1] as List<TransformerEntity>
        val settings = flows[3] as PowerSettingsData
        @Suppress("UNCHECKED_CAST")
        val conduitSettings = flows[4] as List<com.example.data.ConduitSettingsEntity>
        @Suppress("UNCHECKED_CAST")
        val genList = flows[5] as List<com.example.data.GeneratorEntity>
        @Suppress("UNCHECKED_CAST")
        val atsEntitiesList = flows[6] as List<com.example.data.AtsEntity>
        @Suppress("UNCHECKED_CAST")
        val upsEntitiesList = flows[7] as List<com.example.data.UpsEntity>

        val upsSet = settings.ups ?: com.example.data.UpsEntity()
        val pfGlob = settings.pfG ?: com.example.data.PfGlobalEntity()
        val pfTrans = settings.pfT

        val genSet = settings.gen ?: com.example.data.GeneratorEntity()
        val atsSetResolved = settings.ats

        try {
            val cableSizingResult = com.example.engine.coordinator.EngineeringCalculationCoordinator.calculateProjectCableSizing(
                circuits = circuits,
                transformers = transformers
            )
            val normalizedCircuits = circuits.map { normalizeAutoOverride(it) }
            val sizingResults = cableSizingResult.sizingResults.toMutableMap()
            val circuitParents = cableSizingResult.circuitParents.toMutableMap()
            val resolvedList = cableSizingResult.resolvedCircuits.toMutableList()

            val transformerResults = com.example.engine.coordinator.EngineeringCalculationCoordinator.calculateProjectTransformers(
                circuits = normalizedCircuits,
                transformers = transformers
            )

            val drumCap = preferences.drumDefaultCapacity.value
            val safetyMargin = preferences.cableSafetyMargin.value
            val cutOverhead = preferences.cableCutOverhead.value

            val boq = BoqEngine.generateBoq(normalizedCircuits, sizingResults)
            
            val drumAllocations = DrumScheduleEngine.generateDetailedDrumSchedule(
                normalizedCircuits,
                sizingResults,
                maxDrumLength = drumCap,
                safetyMargin = safetyMargin,
                cutOverhead = cutOverhead
            )

            val drums = drumAllocations
            val procurement = ProcurementEngine.generateProcurement(normalizedCircuits, sizingResults)

            // Conduit Fill Engine Calculation
            val conduitResultsMap = mutableMapOf<Int, com.example.engine.ConduitFillResult>()
            resolvedList.forEach { circ ->
                val cSetting = conduitSettings.firstOrNull { it.circuitId == circ.id }
                val sizingRes = sizingResults[circ.id]
                val cableOD = sizingRes?.cableOD_mm ?: 0.0
                val cpcSize = sizingRes?.earthCPC_mm2?.toDoubleOrNull() ?: 0.0
                val cpcMat = sizingRes?.cableMaterial ?: "Cu"

                val fillInput = com.example.engine.ConduitFillInput(
                    circuitId = circ.id,
                    circuitName = circ.circuitName,
                    mainCableOD_mm = cableOD,
                    cpcSizeMm2 = cpcSize,
                    cpcMaterial = cpcMat,
                    parallelRuns = circ.parallelRuns,
                    routeLength_m = circ.length_m,
                    routingMode = cSetting?.routingMode ?: "ONE_PER_CONDUIT",
                    designProfile = cSetting?.designProfile ?: "PROJECT_DESIGN",
                    customFillLimitPct = cSetting?.customFillLimitPct ?: 40.0,
                    isDirectBuried = cSetting?.isDirectBuried ?: false,
                    cpcArrangement = cSetting?.cpcArrangement ?: "INTERNAL_CORE",
                    manualOverrideTradeSize = cSetting?.manualConduitOverride,
                    manualCpcOD_mm = cSetting?.manualCpcOD,
                    manufacturer = circ.manufacturer
                )
                val fillRes = com.example.engine.ConduitFillEngine.calculate(fillInput, Catalog.conduits)
                val isODApprox = sizingRes?.isODApproximated ?: false
                val odSource = sizingRes?.odSourceMfg ?: ""
                val finalRes = if (isODApprox && odSource.isNotEmpty()) {
                    fillRes.copy(
                        validationMessages = fillRes.validationMessages + "OD approx. ($odSource ref.)"
                    )
                } else fillRes

                conduitResultsMap[circ.id] = finalRes
            }


            val conduitProcurementRollup = com.example.engine.ConduitFillEngine.generateProcurementRollup(conduitResultsMap.values.toList())

            val panelNames = normalizedCircuits
                .map { it.fedFrom }
                .distinct()
                .filterNot { it.equals("Transformer", ignoreCase = true) }


            val panels = panelNames.map { name ->
                PanelScheduleEngine.generateSchedule(name, normalizedCircuits)
            }

            val totalConnectedLoad = transformerResults.values.sumOf { it.connectedLoad }
            val generatorResults = com.example.engine.GeneratorEngine.calculateAll(genList, atsEntitiesList, normalizedCircuits, totalConnectedLoad)
            val generatorResult = generatorResults["GEN-01"] ?: generatorResults.values.firstOrNull()
            
            val shortCircuitResults = com.example.engine.coordinator.EngineeringCalculationCoordinator.calculateProjectShortCircuit(
                resolvedCircuits = resolvedList,
                circuitParents = circuitParents,
                sizingResults = sizingResults,
                transformerResults = transformerResults,
                generatorResults = generatorResults
            )

            // Look up the localized short-circuit current at the ATS-included circuits
            val atsCircuitIsc = resolvedList
                .filter { it.atsIncluded || it.assignedAtsId != null }
                .mapNotNull { shortCircuitResults[it.id]?.iscKA }
                .maxOrNull() ?: (transformerResults.values.firstOrNull()?.isc3Phase ?: 0.0)

            val transformerFLC = transformerResults.values.firstOrNull()?.flc ?: 0.0
            val generatorSizesMap = generatorResults.mapValues { it.value.finalSize ?: 0.0 }
            val atsResults = com.example.engine.AtsEngine.calculateAll(
                atsList = atsEntitiesList,
                circuits = normalizedCircuits,
                generatorSelectedSizes = generatorSizesMap,
                transformerFLC = transformerFLC,
                isc3PhaseAtTransformer = atsCircuitIsc,
                totalConnectedLoad = totalConnectedLoad
            )
            val atsResult = atsResults["ATS-01"] ?: atsResults.values.firstOrNull()
            
            val upsResults = com.example.engine.UpsEngine.calculateAll(upsEntitiesList, normalizedCircuits)
            val upsResult = upsResults["UPS-01"] ?: upsResults.values.firstOrNull()
            
            val pfCorrectionResults = com.example.engine.PfCorrectionEngine.calculate(pfGlob, pfTrans, transformers, transformerResults)

            val adsResults = com.example.engine.coordinator.EngineeringCalculationCoordinator.calculateProjectAds(
                resolvedCircuits = resolvedList,
                sizingResults = sizingResults,
                transformerResults = transformerResults
            )

            val protectionResults = com.example.engine.coordinator.EngineeringCalculationCoordinator.calculateProjectProtection(
                resolvedCircuits = resolvedList,
                circuitParents = circuitParents,
                sizingResults = sizingResults,
                shortCircuitResults = shortCircuitResults,
                transformerResults = transformerResults,
                allCircuits = circuits
            )

            val coordinationResults = com.example.engine.coordinator.EngineeringCalculationCoordinator.calculateProjectCoordination(
                resolvedCircuits = resolvedList,
                circuitParents = circuitParents,
                sizingResults = sizingResults,
                transformerResults = transformerResults,
                allCircuits = circuits
            )
            val activeProjId = activeProjectId.value ?: "DEFAULT_PROJECT"
            val earthingEntityVal = settings.earthing ?: com.example.data.EarthingEntity(projectId = activeProjId)
            val maxIsc = shortCircuitResults.values.maxOfOrNull { it.iscKA } ?: (transformerResults.values.firstOrNull()?.isc3Phase ?: 0.0)
            val mainBreaker = transformerResults.values.firstOrNull()?.flc ?: 630.0

            val defaultTarget = if (earthingEntityVal.projectType.equals("Villa", ignoreCase = true)) 5.0 else 2.0
            val effectiveTarget = earthingEntityVal.targetResistanceOverride_ohm?.takeIf { it > 0.0 } ?: defaultTarget

            val earthingInput = com.example.engine.EarthingInput(
                soilResistivity_ohmM = earthingEntityVal.soilResistivity_ohmM,
                rodLength_m = earthingEntityVal.rodLength_m,
                rodDiameter_mm = earthingEntityVal.rodDiameter_mm,
                rodSpacing_m = earthingEntityVal.rodSpacing_m,
                ringConductor_mm2 = earthingEntityVal.ringConductor_mm2,
                ringBurialDepth_m = earthingEntityVal.ringBurialDepth_m,
                kFactor = earthingEntityVal.kFactor,
                projectType = earthingEntityVal.projectType,
                faultCurrent_kA = maxIsc,
                systemVoltage_V = 400.0,
                mainBreakerRating_A = mainBreaker,
                earthFaultCurrent_kA = transformerResults.values.maxOfOrNull { it.isc1Phase } ?: 0.0,
                disconnectionTime_s = earthingEntityVal.disconnectionTime_s,
                totalConnectedKVA = totalConnectedLoad
            )

            val autoDesignRes = if (earthingEntityVal.isAutoDesignEnabled) _autoDesignResultState.value else null

            val earthingResult = com.example.engine.EarthingEngine.calculate(earthingInput, effectiveTarget)
                .copy(autoDesignResult = autoDesignRes)

            val graph = com.example.sld.ElectricalNetworkGraphBuilder.buildGraph(
                activeProjectId = activeProjId,
                transformers = transformers,
                circuits = normalizedCircuits,
                resolvedCircuits = resolvedList,
                circuitParents = circuitParents,
                sizingResults = sizingResults,
                transformerResults = transformerResults,
                shortCircuitResults = shortCircuitResults,
                conduitResults = conduitResultsMap,
                generatorResult = generatorResult,
                atsResult = atsResult,
                atsEntity = atsSetResolved,
                upsResult = upsResult,
                upsEntity = upsSet,
                upsList = upsEntitiesList,
                upsResults = upsResults,
                pfCorrectionResults = pfCorrectionResults,
                generators = genList,
                generatorResults = generatorResults,
                atsList = atsEntitiesList,
                atsResults = atsResults
            )

            val totalFailures = graph.validationIssues.count { it.severity == com.example.sld.SldValidationStatus.INVALID } +
                    sizingResults.values.count { it.status.contains("FAIL") || it.status.contains("✗") }
            val totalReviews = graph.validationIssues.count { it.severity == com.example.sld.SldValidationStatus.DATA_REQUIRED }
            val totalWarnings = graph.validationIssues.count { it.severity == com.example.sld.SldValidationStatus.WARNING } +
                    sizingResults.values.count { it.status.contains("WARNING") || it.status.contains("⚠") }

            val overallStatusStr = when {
                totalFailures > 0 -> "$totalFailures ISSUES REQUIRE ATTENTION"
                totalReviews > 0 -> "$totalReviews ITEMS NEED ENGINEERING REVIEW"
                totalWarnings > 0 -> "$totalWarnings WARNINGS DETECTED"
                else -> "100% COMPLIANT / PASS"
            }

            val validationSummary = ProjectValidationSummary(
                totalFailures = totalFailures,
                totalReviewsRequired = totalReviews,
                totalWarnings = totalWarnings,
                overallStatus = overallStatusStr,
                openIssues = graph.validationIssues
            )

            UiState.Success(
                circuits = normalizedCircuits,
                transformers = transformers,
                sizingResults = sizingResults,
                transformerResults = transformerResults,
                boq = boq,
                drums = drums,
                procurement = procurement,
                panels = panels,
                generatorResult = generatorResult,
                generatorResults = generatorResults,
                generators = genList,
                atsResult = atsResult,
                atsResults = atsResults,
                atsEntity = atsSetResolved,
                atsList = atsEntitiesList,
                upsResult = upsResult,
                upsResults = upsResults,
                upsList = upsEntitiesList,
                pfCorrectionResults = pfCorrectionResults,
                shortCircuitResults = shortCircuitResults,
                adsResults = adsResults,
                protectionResults = protectionResults,
                coordinationResults = coordinationResults,
                earthingResult = earthingResult,
                earthingEntity = earthingEntityVal,
                conduitResults = conduitResultsMap,
                conduitProcurement = conduitProcurementRollup,
                resolvedCircuits = resolvedList,
                circuitParents = circuitParents,
                drumAllocations = drumAllocations,
                validationSummary = validationSummary
            )

        } catch (exception: Exception) {
            UiState.Error(
                exception.message ?: "An engineering calculation error occurred."
            )
        }

    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )

    /** Recalculate without modifying stored circuits, transformers or overrides. */
    fun recalculate() {
        recalculationToken.value = recalculationToken.value + 1
    }

    fun updateCircuit(circuit: CircuitEntity) {

        viewModelScope.launch {
            repository.updateCircuit(normalizeAutoOverride(circuit))
            recalculate()
        }
    }

    fun addCircuit(circuit: CircuitEntity) {
        val projectId = _activeProjectId.value
        if (projectId == null) {
            projectError.value = "Cannot add circuit: No active project selected."
            return
        }
        viewModelScope.launch {
            val withProject = circuit.copy(projectId = projectId)
            repository.insertCircuit(normalizeAutoOverride(withProject))
            recalculate()
        }
    }

    fun deleteCircuit(circuit: CircuitEntity) {
        viewModelScope.launch {
            repository.deleteCircuit(circuit)
            recalculate()
        }
    }


    fun saveGeneratorSettings(settings: com.example.data.GeneratorEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot save generator settings: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = settings.copy(projectId = prj)
            repository.saveGeneratorSettings(withPrj)
        }
    }
    fun saveAtsSettings(settings: com.example.data.AtsEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot save ATS settings: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = settings.copy(projectId = prj)
            repository.saveAtsSettings(withPrj)
        }
    }
    fun saveUpsSettings(settings: com.example.data.UpsEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot save UPS settings: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = settings.copy(projectId = prj)
            repository.saveUpsSettings(withPrj)
        }
    }
    fun savePfGlobalSettings(settings: com.example.data.PfGlobalEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot save PF global settings: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = settings.copy(projectId = prj)
            repository.savePfGlobalSettings(withPrj)
        }
    }
    fun savePfTransformerSettings(settings: com.example.data.PfTransformerEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot save PF transformer settings: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = settings.copy(projectId = prj)
            repository.savePfTransformerSettings(withPrj)
        }
    }

    fun updateTransformer(transformer: TransformerEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot update transformer: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = transformer.copy(projectId = prj)
            repository.updateTransformer(withPrj)
        }
    }

    fun addGenerator(settings: GeneratorEntity = GeneratorEntity()) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.createGeneratorAtomic(prj, settings)
        }
    }

    fun deleteGenerator(generatorId: String) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.deleteGenerator(prj, generatorId)
        }
    }

    fun addAts(settings: AtsEntity = AtsEntity()) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.createAtsAtomic(prj, settings)
        }
    }

    fun deleteAts(atsId: String) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.deleteAts(prj, atsId)
        }
    }

    fun addUps(settings: UpsEntity = UpsEntity()) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.createUpsAtomic(prj, settings)
        }
    }

    fun deleteUps(upsId: String) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.deleteUps(prj, upsId)
        }
    }

    fun insertTransformer(transformer: TransformerEntity) {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot insert transformer: No active project selected."
            return
        }
        viewModelScope.launch {
            val withPrj = transformer.copy(projectId = prj)
            repository.insertTransformer(withPrj)
            selectedTransformerId.value = withPrj.transformerId
        }
    }

    fun deleteTransformer(transformer: TransformerEntity) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            repository.deleteTransformer(transformer.copy(projectId = prj))
            val remaining = repository.getAllTransformers().filter { it.projectId == prj && it.transformerId != transformer.transformerId }
            val fallbackId = remaining.firstOrNull()?.transformerId ?: ""
            
            val circuits = repository.getAllCircuits().filter { it.projectId == prj && it.transformerId == transformer.transformerId }
            circuits.forEach { circ ->
                repository.updateCircuit(circ.copy(transformerId = fallbackId))
            }
            
            selectedTransformerId.value = fallbackId
            recalculate()
        }
    }

    fun addNewTransformerInline(): String {
        val prj = _activeProjectId.value ?: "DEFAULT_PROJECT"
        val existing = uiState.value.let { if (it is UiState.Success) it.transformers else emptyList() }
        val nextNum = existing.size + 1
        val nextId = "TX-${String.format(java.util.Locale.US, "%02d", nextNum)}"
        val tx = createDefaultTransformer(nextId).copy(
            transformerId = nextId,
            transformerName = "Transformer $nextNum",
            projectId = prj
        )
        insertTransformer(tx)
        selectedTransformerId.value = nextId
        return nextId
    }

    fun resetToReferenceData() {
        // Obsolete in Phase 3
    }

    fun startNewEmptyProject() {
        val prj = _activeProjectId.value
        if (prj == null) {
            projectError.value = "Cannot clear project: No active project selected."
            return
        }
        viewModelScope.launch {
            repository.deleteCircuitsForProject(prj)
            repository.deleteTransformersForProject(prj)
            selectedTransformerId.value = ""
            selectedCircuitId.value = null
            recalculate()
        }
    }

    fun onSaveEarthing(settings: com.example.data.EarthingEntity) {
        val prj = _activeProjectId.value ?: return
        if (!settings.isAutoDesignEnabled) {
            _autoDesignResultState.value = null
        }
        viewModelScope.launch {
            repository.saveEarthingSettings(settings.copy(projectId = prj))
        }
    }

    fun onRunAutoDesign() {
        val state = uiState.value as? UiState.Success ?: return
        val earthingEntityVal = state.earthingEntity ?: return
        val defaultTarget = if (earthingEntityVal.projectType.equals("Villa", ignoreCase = true)) 5.0 else 2.0
        val res = com.example.engine.EarthingEngine.recommendConfiguration(
            soilResistivity_ohmM = earthingEntityVal.soilResistivity_ohmM,
            soilResistivitySource = earthingEntityVal.soilResistivitySource,
            userTargetResistance_ohm = earthingEntityVal.targetResistanceOverride_ohm,
            projectTypeDefaultTarget_ohm = defaultTarget,
            designMarginPct = earthingEntityVal.designMarginPct,
            siteLength_m = earthingEntityVal.siteLength_m,
            siteWidth_m = earthingEntityVal.siteWidth_m,
            ringConductor_mm2 = earthingEntityVal.ringConductor_mm2,
            ringBurialDepth_m = earthingEntityVal.ringBurialDepth_m
        )
        _autoDesignResultState.value = res
        recalculate()
    }

    fun onApplyAutoDesignCandidate(candidate: com.example.engine.ElectrodeCandidate) {
        val prj = _activeProjectId.value ?: return
        viewModelScope.launch {
            val currentSettings = (uiState.value as? UiState.Success)?.earthingEntity ?: com.example.data.EarthingEntity(projectId = prj)
            val updated = currentSettings.copy(
                rodLength_m = candidate.rodLength_m,
                rodDiameter_mm = candidate.rodDiameter_mm,
                rodSpacing_m = candidate.rodSpacing_m,
                autoDesignAppliedConfigId = candidate.id
            )
            repository.saveEarthingSettings(updated.copy(projectId = prj))
        }
    }
}


