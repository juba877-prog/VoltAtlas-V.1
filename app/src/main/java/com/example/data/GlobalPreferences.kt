package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlobalPreferences(context: Context, coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
    private val sharedPrefs = context.applicationContext.getSharedPreferences("VoltAtlas_Globals", Context.MODE_PRIVATE)

    /**
     * Explicit, device-local engineer identity used only as the trusted principal for
     * a separately confirmed proposal mutation. It is intentionally blank by default.
     */
    private val _trustedEngineerIdentity = MutableStateFlow("")
    val trustedEngineerIdentity: StateFlow<String> = _trustedEngineerIdentity.asStateFlow()

    fun setTrustedEngineerIdentity(value: String) {
        val normalized = value.trim()
        sharedPrefs.edit().putString("trusted_engineer_identity", normalized).apply()
        _trustedEngineerIdentity.value = normalized
    }

    private val _confirmDestructiveActions = MutableStateFlow(true)
    val confirmDestructiveActions: StateFlow<Boolean> = _confirmDestructiveActions.asStateFlow()

    fun setConfirmDestructiveActions(value: Boolean) {
        sharedPrefs.edit().putBoolean("confirm_destructive_actions", value).apply()
        _confirmDestructiveActions.value = value
    }

    private val _startupBehavior = MutableStateFlow("ALWAYS_SHOW_PICKER")
    val startupBehavior: StateFlow<String> = _startupBehavior.asStateFlow()

    fun setStartupBehavior(value: String) {
        sharedPrefs.edit().putString("startup_behavior", value).apply()
        _startupBehavior.value = value
    }

    private val _themeMode = MutableStateFlow("DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(value: String) {
        sharedPrefs.edit().putString("theme_mode", value).apply()
        _themeMode.value = value
    }

    private val _defaultFrequency = MutableStateFlow(50)
    val defaultFrequency: StateFlow<Int> = _defaultFrequency.asStateFlow()

    fun setDefaultFrequency(value: Int) {
        sharedPrefs.edit().putInt("default_frequency", value).apply()
        _defaultFrequency.value = value
    }

    private val _defaultVoltage3Phase = MutableStateFlow(400)
    val defaultVoltage3Phase: StateFlow<Int> = _defaultVoltage3Phase.asStateFlow()

    fun setDefaultVoltage3Phase(value: Int) {
        sharedPrefs.edit().putInt("default_voltage_3p", value).apply()
        _defaultVoltage3Phase.value = value
    }

    private val _maxVdLimitLighting = MutableStateFlow(3.0)
    val maxVdLimitLighting: StateFlow<Double> = _maxVdLimitLighting.asStateFlow()

    fun setMaxVdLimitLighting(value: Double) {
        sharedPrefs.edit().putFloat("max_vd_lighting", value.toFloat()).apply()
        _maxVdLimitLighting.value = value
    }

    private val _maxVdLimitPower = MutableStateFlow(5.0)
    val maxVdLimitPower: StateFlow<Double> = _maxVdLimitPower.asStateFlow()

    fun setMaxVdLimitPower(value: Double) {
        sharedPrefs.edit().putFloat("max_vd_power", value.toFloat()).apply()
        _maxVdLimitPower.value = value
    }

    private val _defaultConductorMaterial = MutableStateFlow("Cu")
    val defaultConductorMaterial: StateFlow<String> = _defaultConductorMaterial.asStateFlow()

    fun setDefaultConductorMaterial(value: String) {
        sharedPrefs.edit().putString("default_conductor", value).apply()
        _defaultConductorMaterial.value = value
    }

    private val _defaultInsulation = MutableStateFlow("XLPE")
    val defaultInsulation: StateFlow<String> = _defaultInsulation.asStateFlow()

    fun setDefaultInsulation(value: String) {
        sharedPrefs.edit().putString("default_insulation", value).apply()
        _defaultInsulation.value = value
    }

    private val _defaultCoreConfig = MutableStateFlow("4C")
    val defaultCoreConfig: StateFlow<String> = _defaultCoreConfig.asStateFlow()

    fun setDefaultCoreConfig(value: String) {
        sharedPrefs.edit().putString("default_core_config", value).apply()
        _defaultCoreConfig.value = value
    }

    private val _hasCompletedFirstLaunch = MutableStateFlow(false)
    val hasCompletedFirstLaunch: StateFlow<Boolean> = _hasCompletedFirstLaunch.asStateFlow()

    fun setHasCompletedFirstLaunch(value: Boolean) {
        sharedPrefs.edit().putBoolean("has_completed_first_launch", value).apply()
        _hasCompletedFirstLaunch.value = value
    }

    private val _lastOpenedProjectId = MutableStateFlow<String?>(null)
    val lastOpenedProjectId: StateFlow<String?> = _lastOpenedProjectId.asStateFlow()

    fun setLastOpenedProjectId(value: String?) {
        sharedPrefs.edit().putString("last_opened_project_id", value).apply()
        _lastOpenedProjectId.value = value
    }

    private val _fullBootEnabled = MutableStateFlow(false)
    val fullBootEnabled: StateFlow<Boolean> = _fullBootEnabled.asStateFlow()

    fun setFullBootEnabled(value: Boolean) {
        sharedPrefs.edit().putBoolean("full_boot_enabled", value).apply()
        _fullBootEnabled.value = value
    }

    private val _showLastProjectOnStartup = MutableStateFlow(true)
    val showLastProjectOnStartup: StateFlow<Boolean> = _showLastProjectOnStartup.asStateFlow()

    fun setShowLastProjectOnStartup(value: Boolean) {
        sharedPrefs.edit().putBoolean("show_last_project_on_startup", value).apply()
        _showLastProjectOnStartup.value = value
    }

    private val _bootSoundEnabled = MutableStateFlow(false)
    val bootSoundEnabled: StateFlow<Boolean> = _bootSoundEnabled.asStateFlow()

    fun setBootSoundEnabled(value: Boolean) {
        sharedPrefs.edit().putBoolean("boot_sound_enabled", value).apply()
        _bootSoundEnabled.value = value
    }

    private val _drumDefaultCapacity = MutableStateFlow(500.0)
    val drumDefaultCapacity: StateFlow<Double> = _drumDefaultCapacity.asStateFlow()

    fun setDrumDefaultCapacity(value: Double) {
        sharedPrefs.edit().putFloat("drum_default_capacity", value.toFloat()).apply()
        _drumDefaultCapacity.value = value
    }

    private val _cableSafetyMargin = MutableStateFlow(0.05)
    val cableSafetyMargin: StateFlow<Double> = _cableSafetyMargin.asStateFlow()

    fun setCableSafetyMargin(value: Double) {
        sharedPrefs.edit().putFloat("cable_safety_margin", value.toFloat()).apply()
        _cableSafetyMargin.value = value
    }

    private val _cableCutOverhead = MutableStateFlow(1.5)
    val cableCutOverhead: StateFlow<Double> = _cableCutOverhead.asStateFlow()

    fun setCableCutOverhead(value: Double) {
        sharedPrefs.edit().putFloat("cable_cut_overhead", value.toFloat()).apply()
        _cableCutOverhead.value = value
    }

    val loadJob: Job = coroutineScope.launch {
        loadFromDisk()
    }

    suspend fun awaitLoaded() {
        loadJob.join()
    }

    private fun loadFromDisk() {
        _trustedEngineerIdentity.value = sharedPrefs.getString("trusted_engineer_identity", "") ?: ""
        _confirmDestructiveActions.value = sharedPrefs.getBoolean("confirm_destructive_actions", true)
        _startupBehavior.value = sharedPrefs.getString("startup_behavior", "ALWAYS_SHOW_PICKER") ?: "ALWAYS_SHOW_PICKER"
        _themeMode.value = sharedPrefs.getString("theme_mode", "DARK") ?: "DARK"
        _defaultFrequency.value = sharedPrefs.getInt("default_frequency", 50)
        _defaultVoltage3Phase.value = sharedPrefs.getInt("default_voltage_3p", 400)
        _maxVdLimitLighting.value = sharedPrefs.getFloat("max_vd_lighting", 3.0f).toDouble()
        _maxVdLimitPower.value = sharedPrefs.getFloat("max_vd_power", 5.0f).toDouble()
        _defaultConductorMaterial.value = sharedPrefs.getString("default_conductor", "Cu") ?: "Cu"
        _defaultInsulation.value = sharedPrefs.getString("default_insulation", "XLPE") ?: "XLPE"
        _defaultCoreConfig.value = sharedPrefs.getString("default_core_config", "4C") ?: "4C"
        _hasCompletedFirstLaunch.value = sharedPrefs.getBoolean("has_completed_first_launch", false)
        _lastOpenedProjectId.value = sharedPrefs.getString("last_opened_project_id", null)
        _fullBootEnabled.value = sharedPrefs.getBoolean("full_boot_enabled", false)
        _showLastProjectOnStartup.value = sharedPrefs.getBoolean("show_last_project_on_startup", true)
        _bootSoundEnabled.value = sharedPrefs.getBoolean("boot_sound_enabled", false)
        _drumDefaultCapacity.value = sharedPrefs.getFloat("drum_default_capacity", 500.0f).toDouble()
        _cableSafetyMargin.value = sharedPrefs.getFloat("cable_safety_margin", 0.05f).toDouble()
        _cableCutOverhead.value = sharedPrefs.getFloat("cable_cut_overhead", 1.5f).toDouble()
    }

    fun resetToDefaults() {
        sharedPrefs.edit().clear().apply()
        _trustedEngineerIdentity.value = ""
        _confirmDestructiveActions.value = true
        _startupBehavior.value = "ALWAYS_SHOW_PICKER"
        _themeMode.value = "DARK"
        _defaultFrequency.value = 50
        _defaultVoltage3Phase.value = 400
        _maxVdLimitLighting.value = 3.0
        _maxVdLimitPower.value = 5.0
        _defaultConductorMaterial.value = "Cu"
        _defaultInsulation.value = "XLPE"
        _defaultCoreConfig.value = "4C"
        _drumDefaultCapacity.value = 500.0
        _cableSafetyMargin.value = 0.05
        _cableCutOverhead.value = 1.5
    }

    // PDF Report Metadata Persistence (Scoped by projectId)
    fun getReportEngineer(projectId: String): String {
        return sharedPrefs.getString("report_engineer_$projectId", "") ?: ""
    }
    fun setReportEngineer(projectId: String, value: String) {
        sharedPrefs.edit().putString("report_engineer_$projectId", value).apply()
    }

    fun getReportContractor(projectId: String): String {
        return sharedPrefs.getString("report_contractor_$projectId", "") ?: ""
    }
    fun setReportContractor(projectId: String, value: String) {
        sharedPrefs.edit().putString("report_contractor_$projectId", value).apply()
    }

    fun getReportConsultant(projectId: String): String {
        return sharedPrefs.getString("report_consultant_$projectId", "") ?: ""
    }
    fun setReportConsultant(projectId: String, value: String) {
        sharedPrefs.edit().putString("report_consultant_$projectId", value).apply()
    }

    fun getReportOwner(projectId: String): String {
        return sharedPrefs.getString("report_owner_$projectId", "") ?: ""
    }
    fun setReportOwner(projectId: String, value: String) {
        sharedPrefs.edit().putString("report_owner_$projectId", value).apply()
    }

    fun getReportDate(projectId: String): String {
        val defaultDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return sharedPrefs.getString("report_date_$projectId", defaultDate) ?: defaultDate
    }
    fun setReportDate(projectId: String, value: String) {
        sharedPrefs.edit().putString("report_date_$projectId", value).apply()
    }

    fun getReportRevision(projectId: String): String {
        return sharedPrefs.getString("report_revision_$projectId", "A") ?: "A"
    }
    fun setReportRevision(projectId: String, value: String) {
        sharedPrefs.edit().putString("report_revision_$projectId", value).apply()
    }
}
