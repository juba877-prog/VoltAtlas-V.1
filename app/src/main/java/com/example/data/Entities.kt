package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.io.Serializable

enum class PanelType {
    MDB, SMDB, DB, MCC, PANEL, OTHER
}

enum class SourceType {
    TRANSFORMER, GENERATOR, GRID, PANEL
}

@Entity(tableName = "circuits")
data class CircuitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val circuitName: String = "",
    val loadKVA: Double = 0.0,
    val fedFrom: String = "Transformer",            // "Transformer" or name of another circuit (e.g. "MDB-1")
    val transformerId: String = "",      // e.g. "TX-01"
    val loadType: String = "Normal",           // "Normal", "Emergency"
    val phases: String = "3P+N",             // "3P+N", "1P+N"
    val voltageV: Int = 400,              // 400, 230
    val pf: Double = 0.8,                 // power factor, default 0.8
    val safetyFactor: Double = 1.25,       // default 1.25 for MDB, 1.0 for others
    val manufacturer: String = "Elsewedy",       // "Elsewedy"
    val cableType: String = "Cu/XLPE/SWA/PVC",          // e.g. "Al/XLPE/STA/PVC - 4C"
    val installation: String = "Laid in Ducts",       // "Laid in Ducts", "Laid Direct in Ground", "Laid in Free Air"
    val parallelRuns: Int = 1,          // default 1 or 2
    val airTempC: Int = 40,              // default 40
    val gndTempC: Int = 40,              // default 40
    val burialDepth_m: Double = 0.6,      // default 0.6
    val soilRes: Int = 125,               // default 125
    val noCircuits: Int = 1,            // default 1
    val formation: String = "Flat",          // "Flat", "Trefoil"
    val spacing: String = "Touching",            // "Touching", "0.15m", "0.30m"
    val length_m: Double = 50.0,           // default 150
    val maxVDpct: Double = 0.03,           // stored as decimal, e.g. 0.02 for 2%, 0.03 for 3%
    
    // Manual overrides (supports user input to force ratings/sizes)
    val overrideDeviceType: String? = null,
    val overrideDeviceSize: Double? = null,
    val overrideCableSize: String? = null,
    val overrideNeutralSize: String? = null,
    val overrideEarthSize: String? = null,
    
    val isHarmonic: Boolean = false,
    val assignedPhase: String? = null,
    @ColumnInfo(defaultValue = "0")
    val upsBacked: Boolean = false,
    
    // Phase 3: Project & Load Classification
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    @ColumnInfo(defaultValue = "1.0")
    val demandFactor: Double = 1.0,
    val diversityGroup: String? = null,
    @ColumnInfo(defaultValue = "0")
    val generatorIncluded: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val atsIncluded: Boolean = false,
    val assignedGeneratorId: String? = null,
    val assignedAtsId: String? = null,
    val assignedUpsId: String? = null,
    @ColumnInfo(defaultValue = "'Static'")
    val loadNature: String = "Static",
    val startingMethod: String? = null,
    val startingCurrentMultiplier: Double? = null,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,

    // Hierarchy additions (nullable / safe defaults)
    val panelType: String? = null,
    val parentId: Int? = null,
    val level: Int? = null,
    val sourceType: String? = null,
    val directLoadConfirmed: Boolean? = null,
    val phaseImbalanceLimitOverridePct: Double? = null,
    val loadCategory: String? = null,

    // Panel Header Fields (Phase 2)
    val busbarRatingA: Double? = null,
    val scRatingKA: Double? = null,
    val mainIncomingType: String? = null,
    val mainIncomingCount: Int? = null,
    val ipRating: String? = null,
    val panelLocation: String? = null,
    val cableEntry: String? = null,
    val feederDescription: String? = null,
    val numberOfWays: Int? = null,
    @ColumnInfo(defaultValue = "'Cable'")
    val feederType: String = "Cable",
    val buswayMfg: String? = null,
    val buswayModel: String? = null
) : Serializable

@Entity(
    tableName = "transformers",
    primaryKeys = ["projectId", "transformerId"],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["projectId", "transformerId"], unique = true)
    ]
)
data class TransformerEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val transformerId: String = "TX-01", // e.g. "TX-01", "TX-02"
    val transformerName: String = "Transformer 1",
    val kVA: Double = 1000.0,
    val voltage: Int = 400,
    val percentZ: Double = 5.0,
    val xr: Double = 6.0,
    val txType: String = "Dry",
    val sourceType: String = "Utility Grid",
    val status: String = "Active",             // "Active", "Inactive"
    val sizeMode: String = "Auto",           // "Auto", "Manual"
    val manualKVA: Double = 1000.0,
    val demandFactor: Double = 0.8,       // default 0.8
    val diversity: Double = 1.0,          // default 1.0
    val growth: Double = 1.0,             // default 1.0
    val safety: Double = 1.0,              // default 1.0
    @ColumnInfo(defaultValue = "'Dyn11'")
    val vectorGroup: String = "Dyn11",
    @ColumnInfo(defaultValue = "1.0")
    val z0OverZ1: Double = 1.0,
    @ColumnInfo(defaultValue = "0.8")
    val pf: Double = 0.8
)


@Entity(
    tableName = "generator_settings",
    primaryKeys = ["projectId", "id"]
)
data class GeneratorEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val id: Int = 1,
    @ColumnInfo(defaultValue = "'GEN-01'")
    val generatorId: String = "GEN-01",
    @ColumnInfo(defaultValue = "'Generator 1'")
    val generatorName: String = "Generator 1",
    val additionalEssentialLoadKW: Double = 0.0,
    val largestMotorKW: Double = 0.0,
    val motorStartMethod: String = "DOL",
    val motorPf: Double = 0.85,
    val generatorPf: Double = 0.8,
    val demandFactor: Double = 0.8,
    val diversityFactor: Double = 1.0,
    val stepLoadPct: Double = 50.0,
    val oversizingFactor: Double = 1.0,
    val enableManualOverride: Boolean = false,
    val manualSizeKVA: Double? = null
)

@Entity(
    tableName = "ats_settings",
    primaryKeys = ["projectId", "id"]
)
data class AtsEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val id: Int = 1,
    @ColumnInfo(defaultValue = "'ATS-01'")
    val atsId: String = "ATS-01",
    @ColumnInfo(defaultValue = "'ATS 1'")
    val atsName: String = "ATS 1",
    val systemVoltage: Int = 400,
    val systemFrequency: Int = 50,
    val numberOfSources: Int = 2,
    val atsLocation: String = "After Main Device",
    val transferType: String = "Open Transition",
    val utilizationCategory: String = "AC-31A (Resistive)",
    val atsClass: String = "PC (Transfer Switch)",
    val loadCoverage: String = "Emergency Only",
    val ambientTemp: Int = 45,
    val designSafetyFactor: Double = 1.25,
    val futureGrowthFactor: Double = 10.0,
    val approvedManufacturer: String = "ABB",
    val mainsFailureDetectionDelay: Int = 3,
    val generatorStartSignalDelay: Int = 0,
    val generatorWarmUp: Int = 7,
    val mainsReturnDetectionDelay: Int = 60,
    val reTransferDelay: Int = 10,
    val generatorCooldown: Int = 300,
    val enclosureIpRating: String = "IP54",
    val normalSourceId: String? = null,
    val emergencySourceId: String? = null,
    val outputBoardId: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val enableManualOverride: Boolean = false,
    val manualSizeAmp: Double? = null
)

@Entity(
    tableName = "ups_settings",
    primaryKeys = ["projectId", "id"]
)
data class UpsEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val id: Int = 1,
    @ColumnInfo(defaultValue = "'UPS-01'")
    val upsId: String = "UPS-01",
    @ColumnInfo(defaultValue = "'UPS 1'")
    val upsName: String = "UPS 1",
    val systemVoltage: Int = 400,
    val systemFrequency: Int = 50,
    val upsPf: Double = 0.9,
    val upsEfficiency: Double = 94.0,
    val batteryType: String = "VRLA",
    val autonomyMinutes: Int = 15,
    val batteryEodVoltage: Double = 1.67,
    val ambientTemp: Int = 25,
    val tempDeratingFactor: Double = 1.0,
    val agingFactor: Double = 1.25,
    val designMarginPct: Double = 20.0,
    @ColumnInfo(defaultValue = "0")
    val enableManualOverride: Boolean = false,
    val manualSizeKVA: Double? = null
)

@Entity(
    tableName = "pf_global_settings",
    primaryKeys = ["projectId", "id"]
)
data class PfGlobalEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val id: Int = 1,
    val targetPf: Double = 0.97,
    val kvarStepSize: Double = 50.0,
    val systemVoltage: Int = 400,
    val annualOperatingHours: Int = 6000,
    val energyCost: Double = 1.5,
    val lossReductionPct: Double = 0.03,
    val unityToleranceKvar: Double = 0.5
)

@Entity(
    tableName = "pf_transformer_settings",
    primaryKeys = ["projectId", "transformerId"]
)
data class PfTransformerEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val transformerId: String,
    val harmonicFilter: String = "Not Provided",
    val detuningReactor: String = "Not Provided"
)


@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val projectId: String,
    val projectName: String,
    val projectCode: String? = null,
    val clientName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "20.0")
    val defaultPhaseImbalanceLimitPct: Double = 20.0
) : Serializable

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = 0,
    val activeProjectId: String? = null
)

@Entity(
    tableName = "conduit_settings",
    primaryKeys = ["projectId", "circuitId"],
    indices = [Index(value = ["projectId"])]
)
data class ConduitSettingsEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val circuitId: Int,
    @ColumnInfo(defaultValue = "'ONE_PER_CONDUIT'")
    val routingMode: String = "ONE_PER_CONDUIT", // ONE_PER_CONDUIT, ALL_IN_ONE, CUSTOM
    @ColumnInfo(defaultValue = "'PROJECT_DESIGN'")
    val designProfile: String = "PROJECT_DESIGN", // PROJECT_DESIGN, NEC_CHAPTER_9, CUSTOM
    @ColumnInfo(defaultValue = "40.0")
    val customFillLimitPct: Double = 40.0,
    @ColumnInfo(defaultValue = "0")
    val isDirectBuried: Boolean = false,
    @ColumnInfo(defaultValue = "'INTERNAL_CORE'")
    val cpcArrangement: String = "INTERNAL_CORE", // INTERNAL_CORE, SEPARATE_CONDUIT, EXTERNAL, ARMOURED, UNKNOWN
    val manualConduitOverride: String? = null,
    val manualCpcOD: Double? = null
)

@Entity(
    tableName = "earthing_settings",
    primaryKeys = ["projectId", "id"]
)
data class EarthingEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val id: Int = 1,
    val soilResistivity_ohmM: Double = 50.0,
    val rodLength_m: Double = 3.0,
    val rodDiameter_mm: Double = 17.0,
    val rodSpacing_m: Double = 5.0,
    val ringConductor_mm2: Double = 25.0,
    val ringBurialDepth_m: Double = 0.6,
    val kFactor: Double = 159.0,
    val projectType: String = "Commercial",
    val disconnectionTime_s: Double = 0.4,
    val targetResistanceOverride_ohm: Double? = null,
    val soilResistivitySource: String = "ASSUMED",
    val designMarginPct: Double = 10.0,
    val isAutoDesignEnabled: Boolean = false,
    val siteLength_m: Double? = null,
    val siteWidth_m: Double? = null,
    val autoDesignAppliedConfigId: String? = null
)


