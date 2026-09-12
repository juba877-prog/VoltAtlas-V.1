package com.example.ui.dialogs.circuiteditor.model

/**
 * Immutable representation of user-editable draft values in the Circuit Editor.
 *
 * This draft contains ONLY user-editable fields and transient UI-level cable filter
 * selections. It contains zero persisted opaque fields, zero auto-generated IDs,
 * and zero derived engineering flags.
 */
data class CircuitEditorDraft(
    // Step 1: Identity & Source
    val name: String = "",
    val panelType: String = "LOAD",
    val fedFrom: String = "Transformer",
    val transformerId: String = "",

    // Step 2: Load Specifications
    val loadCategory: String? = null,
    val loadNature: String = "Static",
    val startingMethod: String = "DOL",
    val startingCurrentMultiplier: String = "",
    val loadKVA: String = "10.0",
    val pf: String = "0.85",
    val demandFactor: String = "1.0",
    val voltageV: String = "400",
    val safetyFactor: String = "1.0",
    val phases: String = "3P+N",
    val isHarmonic: Boolean = false,

    // Step 3: Supply & Backup
    val supplyCategory: String = "NORMAL", // "NORMAL", "EMERGENCY", "UPS", "EMERGENCY + UPS"
    val assignedAtsId: String = "None",
    val assignedGeneratorId: String = "None",
    val assignedUpsId: String = "None",

    // Step 4: Feeder Selection (Cable / Busway)
    val feederType: String = "Cable", // "Cable" or "Busway"
    val manufacturer: String = "Elsewedy",
    val cableType: String = "",
    val parallelRuns: String = "1",
    val length_m: String = "30",
    val maxVDpct: String = "3.0", // Stored in UI as percent (e.g. "3.0" = 3%), mapped to fraction 0.03
    val buswayMfg: String = "Elsewedy",
    val buswayModel: String = "Power Link",

    // UI-Only Progressive Cable Filters (transient selection state)
    val filterMat: String = "Copper",
    val filterArmour: String = "Armoured",
    val filterCores: String = "4C",
    val filterQuery: String = "",

    // Step 5: Installation Conditions
    val installation: String = "Laid in Ducts",
    val airTempC: String = "40",
    val gndTempC: String = "40",
    val burialDepth_m: String = "0.6",
    val soilRes: String = "125",
    val noCircuits: String = "1",
    val formation: String = "Flat",
    val spacing: String = "Touching",

    // Step 6: Panel Header Equipment (Applicable only when panelType != "LOAD" && panelType.isNotBlank())
    val busbarRatingA: String = "",
    val mainIncomingType: String = "",
    val mainIncomingCount: String = "",
    val ipRating: String = "",
    val panelLocation: String = "",
    val cableEntry: String = "",
    val numberOfWays: String = "",
    val feederDescription: String = ""
)
