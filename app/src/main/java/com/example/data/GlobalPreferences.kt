package com.example.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PreferencesLoadState {
    data object Loading : PreferencesLoadState
    data object Loaded : PreferencesLoadState
    data class Failed(val cause: Throwable) : PreferencesLoadState
}

internal data class PreferencesSnapshot(
    val trustedEngineerIdentity: String,
    val confirmDestructiveActions: Boolean,
    val startupBehavior: String,
    val themeMode: String,
    val defaultFrequency: Int,
    val defaultVoltage3Phase: Int,
    val maxVdLimitLighting: Double,
    val maxVdLimitPower: Double,
    val defaultConductorMaterial: String,
    val defaultInsulation: String,
    val defaultCoreConfig: String,
    val hasCompletedFirstLaunch: Boolean,
    val lastOpenedProjectId: String?,
    val fullBootEnabled: Boolean,
    val showLastProjectOnStartup: Boolean,
    val bootSoundEnabled: Boolean,
    val drumDefaultCapacity: Double,
    val cableSafetyMargin: Double,
    val cableCutOverhead: Double
)

class GlobalPreferences(
    context: Context,
    coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val sharedPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private var globalMutationVersion: Long = 0L
    private val keyVersions = mutableMapOf<String, Long>()

    private val _loadState = MutableStateFlow<PreferencesLoadState>(PreferencesLoadState.Loading)
    val loadState: StateFlow<PreferencesLoadState> = _loadState.asStateFlow()

    @androidx.annotation.VisibleForTesting
    internal var onBeforePublishSnapshot: (suspend () -> Unit)? = null

    /**
     * Explicit, device-local engineer identity used only as the trusted principal for
     * a separately confirmed proposal mutation. It is intentionally blank by default.
     */
    private val _trustedEngineerIdentity = MutableStateFlow(DEFAULT_TRUSTED_ENGINEER_IDENTITY)
    val trustedEngineerIdentity: StateFlow<String> = _trustedEngineerIdentity.asStateFlow()

    fun setTrustedEngineerIdentity(value: String) {
        val normalized = value.trim()
        synchronized(lock) {
            keyVersions[KEY_TRUSTED_ENGINEER_IDENTITY] = (keyVersions[KEY_TRUSTED_ENGINEER_IDENTITY] ?: 0L) + 1L
            _trustedEngineerIdentity.value = normalized
            sharedPrefs.edit().putString(KEY_TRUSTED_ENGINEER_IDENTITY, normalized).apply()
        }
    }

    private val _confirmDestructiveActions = MutableStateFlow(DEFAULT_CONFIRM_DESTRUCTIVE_ACTIONS)
    val confirmDestructiveActions: StateFlow<Boolean> = _confirmDestructiveActions.asStateFlow()

    fun setConfirmDestructiveActions(value: Boolean) {
        synchronized(lock) {
            keyVersions[KEY_CONFIRM_DESTRUCTIVE_ACTIONS] = (keyVersions[KEY_CONFIRM_DESTRUCTIVE_ACTIONS] ?: 0L) + 1L
            _confirmDestructiveActions.value = value
            sharedPrefs.edit().putBoolean(KEY_CONFIRM_DESTRUCTIVE_ACTIONS, value).apply()
        }
    }

    private val _startupBehavior = MutableStateFlow(DEFAULT_STARTUP_BEHAVIOR)
    val startupBehavior: StateFlow<String> = _startupBehavior.asStateFlow()

    fun setStartupBehavior(value: String) {
        synchronized(lock) {
            keyVersions[KEY_STARTUP_BEHAVIOR] = (keyVersions[KEY_STARTUP_BEHAVIOR] ?: 0L) + 1L
            _startupBehavior.value = value
            sharedPrefs.edit().putString(KEY_STARTUP_BEHAVIOR, value).apply()
        }
    }

    private val _themeMode = MutableStateFlow(DEFAULT_THEME_MODE)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(value: String) {
        synchronized(lock) {
            keyVersions[KEY_THEME_MODE] = (keyVersions[KEY_THEME_MODE] ?: 0L) + 1L
            _themeMode.value = value
            sharedPrefs.edit().putString(KEY_THEME_MODE, value).apply()
        }
    }

    private val _defaultFrequency = MutableStateFlow(DEFAULT_DEFAULT_FREQUENCY)
    val defaultFrequency: StateFlow<Int> = _defaultFrequency.asStateFlow()

    fun setDefaultFrequency(value: Int) {
        synchronized(lock) {
            keyVersions[KEY_DEFAULT_FREQUENCY] = (keyVersions[KEY_DEFAULT_FREQUENCY] ?: 0L) + 1L
            _defaultFrequency.value = value
            sharedPrefs.edit().putInt(KEY_DEFAULT_FREQUENCY, value).apply()
        }
    }

    private val _defaultVoltage3Phase = MutableStateFlow(DEFAULT_DEFAULT_VOLTAGE_3P)
    val defaultVoltage3Phase: StateFlow<Int> = _defaultVoltage3Phase.asStateFlow()

    fun setDefaultVoltage3Phase(value: Int) {
        synchronized(lock) {
            keyVersions[KEY_DEFAULT_VOLTAGE_3P] = (keyVersions[KEY_DEFAULT_VOLTAGE_3P] ?: 0L) + 1L
            _defaultVoltage3Phase.value = value
            sharedPrefs.edit().putInt(KEY_DEFAULT_VOLTAGE_3P, value).apply()
        }
    }

    private val _maxVdLimitLighting = MutableStateFlow(DEFAULT_MAX_VD_LIGHTING)
    val maxVdLimitLighting: StateFlow<Double> = _maxVdLimitLighting.asStateFlow()

    fun setMaxVdLimitLighting(value: Double) {
        synchronized(lock) {
            keyVersions[KEY_MAX_VD_LIGHTING] = (keyVersions[KEY_MAX_VD_LIGHTING] ?: 0L) + 1L
            _maxVdLimitLighting.value = value
            sharedPrefs.edit().putFloat(KEY_MAX_VD_LIGHTING, value.toFloat()).apply()
        }
    }

    private val _maxVdLimitPower = MutableStateFlow(DEFAULT_MAX_VD_POWER)
    val maxVdLimitPower: StateFlow<Double> = _maxVdLimitPower.asStateFlow()

    fun setMaxVdLimitPower(value: Double) {
        synchronized(lock) {
            keyVersions[KEY_MAX_VD_POWER] = (keyVersions[KEY_MAX_VD_POWER] ?: 0L) + 1L
            _maxVdLimitPower.value = value
            sharedPrefs.edit().putFloat(KEY_MAX_VD_POWER, value.toFloat()).apply()
        }
    }

    private val _defaultConductorMaterial = MutableStateFlow(DEFAULT_DEFAULT_CONDUCTOR)
    val defaultConductorMaterial: StateFlow<String> = _defaultConductorMaterial.asStateFlow()

    fun setDefaultConductorMaterial(value: String) {
        synchronized(lock) {
            keyVersions[KEY_DEFAULT_CONDUCTOR] = (keyVersions[KEY_DEFAULT_CONDUCTOR] ?: 0L) + 1L
            _defaultConductorMaterial.value = value
            sharedPrefs.edit().putString(KEY_DEFAULT_CONDUCTOR, value).apply()
        }
    }

    private val _defaultInsulation = MutableStateFlow(DEFAULT_DEFAULT_INSULATION)
    val defaultInsulation: StateFlow<String> = _defaultInsulation.asStateFlow()

    fun setDefaultInsulation(value: String) {
        synchronized(lock) {
            keyVersions[KEY_DEFAULT_INSULATION] = (keyVersions[KEY_DEFAULT_INSULATION] ?: 0L) + 1L
            _defaultInsulation.value = value
            sharedPrefs.edit().putString(KEY_DEFAULT_INSULATION, value).apply()
        }
    }

    private val _defaultCoreConfig = MutableStateFlow(DEFAULT_DEFAULT_CORE_CONFIG)
    val defaultCoreConfig: StateFlow<String> = _defaultCoreConfig.asStateFlow()

    fun setDefaultCoreConfig(value: String) {
        synchronized(lock) {
            keyVersions[KEY_DEFAULT_CORE_CONFIG] = (keyVersions[KEY_DEFAULT_CORE_CONFIG] ?: 0L) + 1L
            _defaultCoreConfig.value = value
            sharedPrefs.edit().putString(KEY_DEFAULT_CORE_CONFIG, value).apply()
        }
    }

    private val _hasCompletedFirstLaunch = MutableStateFlow(DEFAULT_HAS_COMPLETED_FIRST_LAUNCH)
    val hasCompletedFirstLaunch: StateFlow<Boolean> = _hasCompletedFirstLaunch.asStateFlow()

    fun setHasCompletedFirstLaunch(value: Boolean) {
        synchronized(lock) {
            keyVersions[KEY_HAS_COMPLETED_FIRST_LAUNCH] = (keyVersions[KEY_HAS_COMPLETED_FIRST_LAUNCH] ?: 0L) + 1L
            _hasCompletedFirstLaunch.value = value
            sharedPrefs.edit().putBoolean(KEY_HAS_COMPLETED_FIRST_LAUNCH, value).apply()
        }
    }

    private val _lastOpenedProjectId = MutableStateFlow<String?>(DEFAULT_LAST_OPENED_PROJECT_ID)
    val lastOpenedProjectId: StateFlow<String?> = _lastOpenedProjectId.asStateFlow()

    fun setLastOpenedProjectId(value: String?) {
        synchronized(lock) {
            keyVersions[KEY_LAST_OPENED_PROJECT_ID] = (keyVersions[KEY_LAST_OPENED_PROJECT_ID] ?: 0L) + 1L
            _lastOpenedProjectId.value = value
            if (value != null) {
                sharedPrefs.edit().putString(KEY_LAST_OPENED_PROJECT_ID, value).apply()
            } else {
                sharedPrefs.edit().remove(KEY_LAST_OPENED_PROJECT_ID).apply()
            }
        }
    }

    private val _fullBootEnabled = MutableStateFlow(DEFAULT_FULL_BOOT_ENABLED)
    val fullBootEnabled: StateFlow<Boolean> = _fullBootEnabled.asStateFlow()

    fun setFullBootEnabled(value: Boolean) {
        synchronized(lock) {
            keyVersions[KEY_FULL_BOOT_ENABLED] = (keyVersions[KEY_FULL_BOOT_ENABLED] ?: 0L) + 1L
            _fullBootEnabled.value = value
            sharedPrefs.edit().putBoolean(KEY_FULL_BOOT_ENABLED, value).apply()
        }
    }

    private val _showLastProjectOnStartup = MutableStateFlow(DEFAULT_SHOW_LAST_PROJECT_ON_STARTUP)
    val showLastProjectOnStartup: StateFlow<Boolean> = _showLastProjectOnStartup.asStateFlow()

    fun setShowLastProjectOnStartup(value: Boolean) {
        synchronized(lock) {
            keyVersions[KEY_SHOW_LAST_PROJECT_ON_STARTUP] = (keyVersions[KEY_SHOW_LAST_PROJECT_ON_STARTUP] ?: 0L) + 1L
            _showLastProjectOnStartup.value = value
            sharedPrefs.edit().putBoolean(KEY_SHOW_LAST_PROJECT_ON_STARTUP, value).apply()
        }
    }

    private val _bootSoundEnabled = MutableStateFlow(DEFAULT_BOOT_SOUND_ENABLED)
    val bootSoundEnabled: StateFlow<Boolean> = _bootSoundEnabled.asStateFlow()

    fun setBootSoundEnabled(value: Boolean) {
        synchronized(lock) {
            keyVersions[KEY_BOOT_SOUND_ENABLED] = (keyVersions[KEY_BOOT_SOUND_ENABLED] ?: 0L) + 1L
            _bootSoundEnabled.value = value
            sharedPrefs.edit().putBoolean(KEY_BOOT_SOUND_ENABLED, value).apply()
        }
    }

    private val _drumDefaultCapacity = MutableStateFlow(DEFAULT_DRUM_DEFAULT_CAPACITY)
    val drumDefaultCapacity: StateFlow<Double> = _drumDefaultCapacity.asStateFlow()

    fun setDrumDefaultCapacity(value: Double) {
        synchronized(lock) {
            keyVersions[KEY_DRUM_DEFAULT_CAPACITY] = (keyVersions[KEY_DRUM_DEFAULT_CAPACITY] ?: 0L) + 1L
            _drumDefaultCapacity.value = value
            sharedPrefs.edit().putFloat(KEY_DRUM_DEFAULT_CAPACITY, value.toFloat()).apply()
        }
    }

    private val _cableSafetyMargin = MutableStateFlow(DEFAULT_CABLE_SAFETY_MARGIN)
    val cableSafetyMargin: StateFlow<Double> = _cableSafetyMargin.asStateFlow()

    fun setCableSafetyMargin(value: Double) {
        synchronized(lock) {
            keyVersions[KEY_CABLE_SAFETY_MARGIN] = (keyVersions[KEY_CABLE_SAFETY_MARGIN] ?: 0L) + 1L
            _cableSafetyMargin.value = value
            sharedPrefs.edit().putFloat(KEY_CABLE_SAFETY_MARGIN, value.toFloat()).apply()
        }
    }

    private val _cableCutOverhead = MutableStateFlow(DEFAULT_CABLE_CUT_OVERHEAD)
    val cableCutOverhead: StateFlow<Double> = _cableCutOverhead.asStateFlow()

    fun setCableCutOverhead(value: Double) {
        synchronized(lock) {
            keyVersions[KEY_CABLE_CUT_OVERHEAD] = (keyVersions[KEY_CABLE_CUT_OVERHEAD] ?: 0L) + 1L
            _cableCutOverhead.value = value
            sharedPrefs.edit().putFloat(KEY_CABLE_CUT_OVERHEAD, value.toFloat()).apply()
        }
    }

    private val initialGlobalVersion: Long = synchronized(lock) {
        globalMutationVersion
    }

    private val initialVersions: Map<String, Long> = synchronized(lock) {
        keyVersions.toMap()
    }

    val loadJob: Job = coroutineScope.launch(ioDispatcher) {
        try {
            loadFromDisk(initialGlobalVersion, initialVersions)
            _loadState.value = PreferencesLoadState.Loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _loadState.value = PreferencesLoadState.Failed(e)
            android.util.Log.w("GlobalPreferences", "Failed to load global preferences from disk", e)
        }
    }

    suspend fun awaitLoaded(): PreferencesLoadState {
        loadJob.join()
        return _loadState.value
    }

    private suspend fun loadFromDisk(globalVersionAtStart: Long, versionsAtStart: Map<String, Long>) {
        val snapshot = readSnapshotFromDisk()

        onBeforePublishSnapshot?.invoke()

        synchronized(lock) {
            publishSnapshotIfUnchanged(snapshot, globalVersionAtStart, versionsAtStart)
        }
    }

    private fun readSnapshotFromDisk(): PreferencesSnapshot {
        return PreferencesSnapshot(
            trustedEngineerIdentity = sharedPrefs.getString(KEY_TRUSTED_ENGINEER_IDENTITY, DEFAULT_TRUSTED_ENGINEER_IDENTITY) ?: DEFAULT_TRUSTED_ENGINEER_IDENTITY,
            confirmDestructiveActions = sharedPrefs.getBoolean(KEY_CONFIRM_DESTRUCTIVE_ACTIONS, DEFAULT_CONFIRM_DESTRUCTIVE_ACTIONS),
            startupBehavior = sharedPrefs.getString(KEY_STARTUP_BEHAVIOR, DEFAULT_STARTUP_BEHAVIOR) ?: DEFAULT_STARTUP_BEHAVIOR,
            themeMode = sharedPrefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE,
            defaultFrequency = sharedPrefs.getInt(KEY_DEFAULT_FREQUENCY, DEFAULT_DEFAULT_FREQUENCY),
            defaultVoltage3Phase = sharedPrefs.getInt(KEY_DEFAULT_VOLTAGE_3P, DEFAULT_DEFAULT_VOLTAGE_3P),
            maxVdLimitLighting = sharedPrefs.getFloat(KEY_MAX_VD_LIGHTING, DEFAULT_MAX_VD_LIGHTING.toFloat()).toDouble(),
            maxVdLimitPower = sharedPrefs.getFloat(KEY_MAX_VD_POWER, DEFAULT_MAX_VD_POWER.toFloat()).toDouble(),
            defaultConductorMaterial = sharedPrefs.getString(KEY_DEFAULT_CONDUCTOR, DEFAULT_DEFAULT_CONDUCTOR) ?: DEFAULT_DEFAULT_CONDUCTOR,
            defaultInsulation = sharedPrefs.getString(KEY_DEFAULT_INSULATION, DEFAULT_DEFAULT_INSULATION) ?: DEFAULT_DEFAULT_INSULATION,
            defaultCoreConfig = sharedPrefs.getString(KEY_DEFAULT_CORE_CONFIG, DEFAULT_DEFAULT_CORE_CONFIG) ?: DEFAULT_DEFAULT_CORE_CONFIG,
            hasCompletedFirstLaunch = sharedPrefs.getBoolean(KEY_HAS_COMPLETED_FIRST_LAUNCH, DEFAULT_HAS_COMPLETED_FIRST_LAUNCH),
            lastOpenedProjectId = sharedPrefs.getString(KEY_LAST_OPENED_PROJECT_ID, DEFAULT_LAST_OPENED_PROJECT_ID),
            fullBootEnabled = sharedPrefs.getBoolean(KEY_FULL_BOOT_ENABLED, DEFAULT_FULL_BOOT_ENABLED),
            showLastProjectOnStartup = sharedPrefs.getBoolean(KEY_SHOW_LAST_PROJECT_ON_STARTUP, DEFAULT_SHOW_LAST_PROJECT_ON_STARTUP),
            bootSoundEnabled = sharedPrefs.getBoolean(KEY_BOOT_SOUND_ENABLED, DEFAULT_BOOT_SOUND_ENABLED),
            drumDefaultCapacity = sharedPrefs.getFloat(KEY_DRUM_DEFAULT_CAPACITY, DEFAULT_DRUM_DEFAULT_CAPACITY.toFloat()).toDouble(),
            cableSafetyMargin = sharedPrefs.getFloat(KEY_CABLE_SAFETY_MARGIN, DEFAULT_CABLE_SAFETY_MARGIN.toFloat()).toDouble(),
            cableCutOverhead = sharedPrefs.getFloat(KEY_CABLE_CUT_OVERHEAD, DEFAULT_CABLE_CUT_OVERHEAD.toFloat()).toDouble()
        )
    }

    private fun publishSnapshotIfUnchanged(
        snapshot: PreferencesSnapshot,
        globalVersionAtStart: Long,
        versionsAtStart: Map<String, Long>
    ) {
        if (globalMutationVersion != globalVersionAtStart) {
            // A global mutation (such as resetToDefaults) occurred; prevent publishing older disk snapshot
            return
        }

        fun isUnchanged(key: String): Boolean {
            val currentVersion = keyVersions[key] ?: 0L
            val startVersion = versionsAtStart[key] ?: 0L
            return currentVersion == startVersion
        }

        if (isUnchanged(KEY_TRUSTED_ENGINEER_IDENTITY)) _trustedEngineerIdentity.value = snapshot.trustedEngineerIdentity
        if (isUnchanged(KEY_CONFIRM_DESTRUCTIVE_ACTIONS)) _confirmDestructiveActions.value = snapshot.confirmDestructiveActions
        if (isUnchanged(KEY_STARTUP_BEHAVIOR)) _startupBehavior.value = snapshot.startupBehavior
        if (isUnchanged(KEY_THEME_MODE)) _themeMode.value = snapshot.themeMode
        if (isUnchanged(KEY_DEFAULT_FREQUENCY)) _defaultFrequency.value = snapshot.defaultFrequency
        if (isUnchanged(KEY_DEFAULT_VOLTAGE_3P)) _defaultVoltage3Phase.value = snapshot.defaultVoltage3Phase
        if (isUnchanged(KEY_MAX_VD_LIGHTING)) _maxVdLimitLighting.value = snapshot.maxVdLimitLighting
        if (isUnchanged(KEY_MAX_VD_POWER)) _maxVdLimitPower.value = snapshot.maxVdLimitPower
        if (isUnchanged(KEY_DEFAULT_CONDUCTOR)) _defaultConductorMaterial.value = snapshot.defaultConductorMaterial
        if (isUnchanged(KEY_DEFAULT_INSULATION)) _defaultInsulation.value = snapshot.defaultInsulation
        if (isUnchanged(KEY_DEFAULT_CORE_CONFIG)) _defaultCoreConfig.value = snapshot.defaultCoreConfig
        if (isUnchanged(KEY_HAS_COMPLETED_FIRST_LAUNCH)) _hasCompletedFirstLaunch.value = snapshot.hasCompletedFirstLaunch
        if (isUnchanged(KEY_LAST_OPENED_PROJECT_ID)) _lastOpenedProjectId.value = snapshot.lastOpenedProjectId
        if (isUnchanged(KEY_FULL_BOOT_ENABLED)) _fullBootEnabled.value = snapshot.fullBootEnabled
        if (isUnchanged(KEY_SHOW_LAST_PROJECT_ON_STARTUP)) _showLastProjectOnStartup.value = snapshot.showLastProjectOnStartup
        if (isUnchanged(KEY_BOOT_SOUND_ENABLED)) _bootSoundEnabled.value = snapshot.bootSoundEnabled
        if (isUnchanged(KEY_DRUM_DEFAULT_CAPACITY)) _drumDefaultCapacity.value = snapshot.drumDefaultCapacity
        if (isUnchanged(KEY_CABLE_SAFETY_MARGIN)) _cableSafetyMargin.value = snapshot.cableSafetyMargin
        if (isUnchanged(KEY_CABLE_CUT_OVERHEAD)) _cableCutOverhead.value = snapshot.cableCutOverhead
    }

    fun resetToDefaults() {
        synchronized(lock) {
            globalMutationVersion++
            ALL_TRACKED_KEYS.forEach { key ->
                keyVersions[key] = (keyVersions[key] ?: 0L) + 1L
            }
            _trustedEngineerIdentity.value = DEFAULT_TRUSTED_ENGINEER_IDENTITY
            _confirmDestructiveActions.value = DEFAULT_CONFIRM_DESTRUCTIVE_ACTIONS
            _startupBehavior.value = DEFAULT_STARTUP_BEHAVIOR
            _themeMode.value = DEFAULT_THEME_MODE
            _defaultFrequency.value = DEFAULT_DEFAULT_FREQUENCY
            _defaultVoltage3Phase.value = DEFAULT_DEFAULT_VOLTAGE_3P
            _maxVdLimitLighting.value = DEFAULT_MAX_VD_LIGHTING
            _maxVdLimitPower.value = DEFAULT_MAX_VD_POWER
            _defaultConductorMaterial.value = DEFAULT_DEFAULT_CONDUCTOR
            _defaultInsulation.value = DEFAULT_DEFAULT_INSULATION
            _defaultCoreConfig.value = DEFAULT_DEFAULT_CORE_CONFIG
            _hasCompletedFirstLaunch.value = DEFAULT_HAS_COMPLETED_FIRST_LAUNCH
            _lastOpenedProjectId.value = DEFAULT_LAST_OPENED_PROJECT_ID
            _fullBootEnabled.value = DEFAULT_FULL_BOOT_ENABLED
            _showLastProjectOnStartup.value = DEFAULT_SHOW_LAST_PROJECT_ON_STARTUP
            _bootSoundEnabled.value = DEFAULT_BOOT_SOUND_ENABLED
            _drumDefaultCapacity.value = DEFAULT_DRUM_DEFAULT_CAPACITY
            _cableSafetyMargin.value = DEFAULT_CABLE_SAFETY_MARGIN
            _cableCutOverhead.value = DEFAULT_CABLE_CUT_OVERHEAD

            sharedPrefs.edit().clear().apply()
        }
    }

    // PDF Report Metadata Persistence (Scoped by projectId)
    fun getReportEngineer(projectId: String): String = sharedPrefs.getString("report_engineer_$projectId", "") ?: ""
    fun setReportEngineer(projectId: String, value: String) = sharedPrefs.edit().putString("report_engineer_$projectId", value).apply()

    fun getReportContractor(projectId: String): String = sharedPrefs.getString("report_contractor_$projectId", "") ?: ""
    fun setReportContractor(projectId: String, value: String) = sharedPrefs.edit().putString("report_contractor_$projectId", value).apply()

    fun getReportConsultant(projectId: String): String = sharedPrefs.getString("report_consultant_$projectId", "") ?: ""
    fun setReportConsultant(projectId: String, value: String) = sharedPrefs.edit().putString("report_consultant_$projectId", value).apply()

    fun getReportOwner(projectId: String): String = sharedPrefs.getString("report_owner_$projectId", "") ?: ""
    fun setReportOwner(projectId: String, value: String) = sharedPrefs.edit().putString("report_owner_$projectId", value).apply()

    fun getReportDate(projectId: String): String {
        val defaultDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return sharedPrefs.getString("report_date_$projectId", defaultDate) ?: defaultDate
    }
    fun setReportDate(projectId: String, value: String) = sharedPrefs.edit().putString("report_date_$projectId", value).apply()

    fun getReportRevision(projectId: String): String = sharedPrefs.getString("report_revision_$projectId", "A") ?: "A"
    fun setReportRevision(projectId: String, value: String) = sharedPrefs.edit().putString("report_revision_$projectId", value).apply()

    companion object {
        const val PREFERENCES_NAME = "VoltAtlas_Globals"

        const val KEY_TRUSTED_ENGINEER_IDENTITY = "trusted_engineer_identity"
        const val KEY_CONFIRM_DESTRUCTIVE_ACTIONS = "confirm_destructive_actions"
        const val KEY_STARTUP_BEHAVIOR = "startup_behavior"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DEFAULT_FREQUENCY = "default_frequency"
        const val KEY_DEFAULT_VOLTAGE_3P = "default_voltage_3p"
        const val KEY_MAX_VD_LIGHTING = "max_vd_lighting"
        const val KEY_MAX_VD_POWER = "max_vd_power"
        const val KEY_DEFAULT_CONDUCTOR = "default_conductor"
        const val KEY_DEFAULT_INSULATION = "default_insulation"
        const val KEY_DEFAULT_CORE_CONFIG = "default_core_config"
        const val KEY_HAS_COMPLETED_FIRST_LAUNCH = "has_completed_first_launch"
        const val KEY_LAST_OPENED_PROJECT_ID = "last_opened_project_id"
        const val KEY_FULL_BOOT_ENABLED = "full_boot_enabled"
        const val KEY_SHOW_LAST_PROJECT_ON_STARTUP = "show_last_project_on_startup"
        const val KEY_BOOT_SOUND_ENABLED = "boot_sound_enabled"
        const val KEY_DRUM_DEFAULT_CAPACITY = "drum_default_capacity"
        const val KEY_CABLE_SAFETY_MARGIN = "cable_safety_margin"
        const val KEY_CABLE_CUT_OVERHEAD = "cable_cut_overhead"

        const val DEFAULT_TRUSTED_ENGINEER_IDENTITY = ""
        const val DEFAULT_CONFIRM_DESTRUCTIVE_ACTIONS = true
        const val DEFAULT_STARTUP_BEHAVIOR = "ALWAYS_SHOW_PICKER"
        const val DEFAULT_THEME_MODE = "DARK"
        const val DEFAULT_DEFAULT_FREQUENCY = 50
        const val DEFAULT_DEFAULT_VOLTAGE_3P = 400
        const val DEFAULT_MAX_VD_LIGHTING = 3.0
        const val DEFAULT_MAX_VD_POWER = 5.0
        const val DEFAULT_DEFAULT_CONDUCTOR = "Cu"
        const val DEFAULT_DEFAULT_INSULATION = "XLPE"
        const val DEFAULT_DEFAULT_CORE_CONFIG = "4C"
        const val DEFAULT_HAS_COMPLETED_FIRST_LAUNCH = false
        val DEFAULT_LAST_OPENED_PROJECT_ID: String? = null
        const val DEFAULT_FULL_BOOT_ENABLED = false
        const val DEFAULT_SHOW_LAST_PROJECT_ON_STARTUP = true
        const val DEFAULT_BOOT_SOUND_ENABLED = false
        const val DEFAULT_DRUM_DEFAULT_CAPACITY = 500.0
        const val DEFAULT_CABLE_SAFETY_MARGIN = 0.05
        const val DEFAULT_CABLE_CUT_OVERHEAD = 1.5

        val ALL_TRACKED_KEYS = listOf(
            KEY_TRUSTED_ENGINEER_IDENTITY,
            KEY_CONFIRM_DESTRUCTIVE_ACTIONS,
            KEY_STARTUP_BEHAVIOR,
            KEY_THEME_MODE,
            KEY_DEFAULT_FREQUENCY,
            KEY_DEFAULT_VOLTAGE_3P,
            KEY_MAX_VD_LIGHTING,
            KEY_MAX_VD_POWER,
            KEY_DEFAULT_CONDUCTOR,
            KEY_DEFAULT_INSULATION,
            KEY_DEFAULT_CORE_CONFIG,
            KEY_HAS_COMPLETED_FIRST_LAUNCH,
            KEY_LAST_OPENED_PROJECT_ID,
            KEY_FULL_BOOT_ENABLED,
            KEY_SHOW_LAST_PROJECT_ON_STARTUP,
            KEY_BOOT_SOUND_ENABLED,
            KEY_DRUM_DEFAULT_CAPACITY,
            KEY_CABLE_SAFETY_MARGIN,
            KEY_CABLE_CUT_OVERHEAD
        )

        @Volatile
        private var INSTANCE: GlobalPreferences? = null

        @Volatile
        private var instanceScope: CoroutineScope? = null

        fun getInstance(
            context: Context,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): GlobalPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val appScope = CoroutineScope(SupervisorJob() + ioDispatcher)
                    instanceScope = appScope
                    GlobalPreferences(
                        context = appContext,
                        coroutineScope = appScope,
                        ioDispatcher = ioDispatcher
                    ).also { INSTANCE = it }
                }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstanceForTesting() {
            val scopeToCancel = synchronized(this) {
                val scope = instanceScope
                INSTANCE = null
                instanceScope = null
                scope
            }
            scopeToCancel?.coroutineContext?.get(Job)?.cancel()
        }
    }
}
