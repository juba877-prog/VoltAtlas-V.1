package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalPreferences(context: Context) {
    private val sharedPrefs = context.applicationContext.getSharedPreferences("VoltAtlas_Globals", Context.MODE_PRIVATE)

    private val _confirmDestructiveActions = MutableStateFlow(
        sharedPrefs.getBoolean("confirm_destructive_actions", true)
    )
    val confirmDestructiveActions: StateFlow<Boolean> = _confirmDestructiveActions.asStateFlow()

    fun setConfirmDestructiveActions(value: Boolean) {
        sharedPrefs.edit().putBoolean("confirm_destructive_actions", value).apply()
        _confirmDestructiveActions.value = value
    }

    private val _startupBehavior = MutableStateFlow(
        sharedPrefs.getString("startup_behavior", "ALWAYS_SHOW_PICKER") ?: "ALWAYS_SHOW_PICKER"
    )
    val startupBehavior: StateFlow<String> = _startupBehavior.asStateFlow()

    fun setStartupBehavior(value: String) {
        sharedPrefs.edit().putString("startup_behavior", value).apply()
        _startupBehavior.value = value
    }

    private val _themeMode = MutableStateFlow(
        sharedPrefs.getString("theme_mode", "DARK") ?: "DARK"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(value: String) {
        sharedPrefs.edit().putString("theme_mode", value).apply()
        _themeMode.value = value
    }

    private val _defaultFrequency = MutableStateFlow(
        sharedPrefs.getInt("default_frequency", 50)
    )
    val defaultFrequency: StateFlow<Int> = _defaultFrequency.asStateFlow()

    fun setDefaultFrequency(value: Int) {
        sharedPrefs.edit().putInt("default_frequency", value).apply()
        _defaultFrequency.value = value
    }

    private val _defaultVoltage3Phase = MutableStateFlow(
        sharedPrefs.getInt("default_voltage_3p", 400)
    )
    val defaultVoltage3Phase: StateFlow<Int> = _defaultVoltage3Phase.asStateFlow()

    fun setDefaultVoltage3Phase(value: Int) {
        sharedPrefs.edit().putInt("default_voltage_3p", value).apply()
        _defaultVoltage3Phase.value = value
    }

    private val _maxVdLimitLighting = MutableStateFlow(
        sharedPrefs.getFloat("max_vd_lighting", 3.0f).toDouble()
    )
    val maxVdLimitLighting: StateFlow<Double> = _maxVdLimitLighting.asStateFlow()

    fun setMaxVdLimitLighting(value: Double) {
        sharedPrefs.edit().putFloat("max_vd_lighting", value.toFloat()).apply()
        _maxVdLimitLighting.value = value
    }

    private val _maxVdLimitPower = MutableStateFlow(
        sharedPrefs.getFloat("max_vd_power", 5.0f).toDouble()
    )
    val maxVdLimitPower: StateFlow<Double> = _maxVdLimitPower.asStateFlow()

    fun setMaxVdLimitPower(value: Double) {
        sharedPrefs.edit().putFloat("max_vd_power", value.toFloat()).apply()
        _maxVdLimitPower.value = value
    }

    private val _defaultConductorMaterial = MutableStateFlow(
        sharedPrefs.getString("default_conductor", "Cu") ?: "Cu"
    )
    val defaultConductorMaterial: StateFlow<String> = _defaultConductorMaterial.asStateFlow()

    fun setDefaultConductorMaterial(value: String) {
        sharedPrefs.edit().putString("default_conductor", value).apply()
        _defaultConductorMaterial.value = value
    }

    private val _defaultInsulation = MutableStateFlow(
        sharedPrefs.getString("default_insulation", "XLPE") ?: "XLPE"
    )
    val defaultInsulation: StateFlow<String> = _defaultInsulation.asStateFlow()

    fun setDefaultInsulation(value: String) {
        sharedPrefs.edit().putString("default_insulation", value).apply()
        _defaultInsulation.value = value
    }

    private val _defaultCoreConfig = MutableStateFlow(
        sharedPrefs.getString("default_core_config", "4C") ?: "4C"
    )
    val defaultCoreConfig: StateFlow<String> = _defaultCoreConfig.asStateFlow()

    fun setDefaultCoreConfig(value: String) {
        sharedPrefs.edit().putString("default_core_config", value).apply()
        _defaultCoreConfig.value = value
    }

    private val _hasCompletedFirstLaunch = MutableStateFlow(
        sharedPrefs.getBoolean("has_completed_first_launch", false)
    )
    val hasCompletedFirstLaunch: StateFlow<Boolean> = _hasCompletedFirstLaunch.asStateFlow()

    fun setHasCompletedFirstLaunch(value: Boolean) {
        sharedPrefs.edit().putBoolean("has_completed_first_launch", value).apply()
        _hasCompletedFirstLaunch.value = value
    }

    private val _lastOpenedProjectId = MutableStateFlow(
        sharedPrefs.getString("last_opened_project_id", null)
    )
    val lastOpenedProjectId: StateFlow<String?> = _lastOpenedProjectId.asStateFlow()

    fun setLastOpenedProjectId(value: String?) {
        sharedPrefs.edit().putString("last_opened_project_id", value).apply()
        _lastOpenedProjectId.value = value
    }

    private val _fullBootEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("full_boot_enabled", false)
    )
    val fullBootEnabled: StateFlow<Boolean> = _fullBootEnabled.asStateFlow()

    fun setFullBootEnabled(value: Boolean) {
        sharedPrefs.edit().putBoolean("full_boot_enabled", value).apply()
        _fullBootEnabled.value = value
    }

    private val _showLastProjectOnStartup = MutableStateFlow(
        sharedPrefs.getBoolean("show_last_project_on_startup", true)
    )
    val showLastProjectOnStartup: StateFlow<Boolean> = _showLastProjectOnStartup.asStateFlow()

    fun setShowLastProjectOnStartup(value: Boolean) {
        sharedPrefs.edit().putBoolean("show_last_project_on_startup", value).apply()
        _showLastProjectOnStartup.value = value
    }

    private val _bootSoundEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("boot_sound_enabled", false)
    )
    val bootSoundEnabled: StateFlow<Boolean> = _bootSoundEnabled.asStateFlow()

    fun setBootSoundEnabled(value: Boolean) {
        sharedPrefs.edit().putBoolean("boot_sound_enabled", value).apply()
        _bootSoundEnabled.value = value
    }

    private val _drumDefaultCapacity = MutableStateFlow(
        sharedPrefs.getFloat("drum_default_capacity", 500.0f).toDouble()
    )
    val drumDefaultCapacity: StateFlow<Double> = _drumDefaultCapacity.asStateFlow()

    fun setDrumDefaultCapacity(value: Double) {
        sharedPrefs.edit().putFloat("drum_default_capacity", value.toFloat()).apply()
        _drumDefaultCapacity.value = value
    }

    private val _cableSafetyMargin = MutableStateFlow(
        sharedPrefs.getFloat("cable_safety_margin", 0.05f).toDouble()
    )
    val cableSafetyMargin: StateFlow<Double> = _cableSafetyMargin.asStateFlow()

    fun setCableSafetyMargin(value: Double) {
        sharedPrefs.edit().putFloat("cable_safety_margin", value.toFloat()).apply()
        _cableSafetyMargin.value = value
    }

    private val _cableCutOverhead = MutableStateFlow(
        sharedPrefs.getFloat("cable_cut_overhead", 1.5f).toDouble()
    )
    val cableCutOverhead: StateFlow<Double> = _cableCutOverhead.asStateFlow()

    fun setCableCutOverhead(value: Double) {
        sharedPrefs.edit().putFloat("cable_cut_overhead", value.toFloat()).apply()
        _cableCutOverhead.value = value
    }

    fun resetToDefaults() {
        sharedPrefs.edit().clear().apply()
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

