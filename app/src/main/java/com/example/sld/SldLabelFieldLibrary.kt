package com.example.sld

/**
 * IEC / one-line-diagram Label-Field Source of Truth (Roadmap §1.2).
 *
 * Formal audit of every professional SLD label field (NFM Consulting / EEP
 * one-line diagram best practices, as synthesized in the Master Roadmap §1.2)
 * against VoltAtlas's [SldDataBlock] rendering per equipment class.
 *
 * Policy:
 *  - Every checklist field maps to exactly one [SldDataBlock] slot (or an
 *    explicit annotation route) and to the data source that populates it.
 *  - [LabelFieldAuditStatus.RENDERED] fields are live end-to-end.
 *  - [LabelFieldAuditStatus.DATA_AVAILABLE_NOT_RENDERED] fields have a data
 *    source but no slot — adding the slot is the next permitted change.
 *  - [LabelFieldAuditStatus.PENDING_DATA_MODEL] fields cannot be rendered yet
 *    because no entity/table stores the value — a data-model change must come
 *    first (tracked in notes; feeding Roadmap §3.2 toggleable detail levels).
 *  - This file contains ZERO rendering logic; it is the audit record and the
 *    contract enforced by [com.example.sld.SldLabelFieldLibraryTest].
 */
enum class LabelFieldAuditStatus {
    /** Populated end-to-end: source data → SldDataBlock slot → (details sheet / canvas). */
    RENDERED,
    /** Source data exists in the data model but no SldDataBlock slot is populated yet. */
    DATA_AVAILABLE_NOT_RENDERED,
    /** No entity/table stores this value yet — data-model work required first. */
    PENDING_DATA_MODEL,
    /** The checklist field does not apply to this equipment class. */
    NOT_APPLICABLE
}

/** Audit record for one checklist field of one equipment class. */
data class LabelFieldSpec(
    /** Equipment class from the §1.2 checklist: UTILITY_SOURCE, TRANSFORMER, SWITCHGEAR_DEVICE, BUSBAR_PANEL. */
    val equipmentClass: String,
    /** Stable field identifier. */
    val fieldId: String,
    /** What the professional checklist requires to be shown. */
    val checklistRequirement: String,
    /** The [SldDataBlock] property (or annotation route) carrying the value. */
    val dataBlockSlot: String,
    /** Where the value comes from (entity / engine result). */
    val populationSource: String,
    val auditStatus: LabelFieldAuditStatus,
    val notes: String = ""
)

object SldLabelFieldLibrary {

    /** Equipment classes from the Roadmap §1.2 checklist. */
    val EQUIPMENT_CLASSES = setOf("UTILITY_SOURCE", "TRANSFORMER", "SWITCHGEAR_DEVICE", "BUSBAR_PANEL")

    val labelFields: List<LabelFieldSpec> = listOf(
        // ---- UTILITY SOURCE ----
        LabelFieldSpec(
            equipmentClass = "UTILITY_SOURCE",
            fieldId = "serviceVoltage",
            checklistRequirement = "Service voltage at the point of supply.",
            dataBlockSlot = "SldDataBlock.voltage (also echoed in SldNode.voltage / display name)",
            populationSource = "ElectricalNetworkGraphBuilder grid node (primaryVoltageStr)",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "UTILITY_SOURCE",
            fieldId = "availableFaultCurrent3Ph",
            checklistRequirement = "Available 3-phase symmetrical fault current at the supply point.",
            dataBlockSlot = "— (no slot populated)",
            populationSource = "Not modeled: the grid node carries no fault level; fault study begins at the transformer (isc3PhaseKa).",
            auditStatus = LabelFieldAuditStatus.PENDING_DATA_MODEL,
            notes = "Requires a utility source fault-level input (e.g. grid Isc kVA/kA) in the data model before it can render."
        ),
        LabelFieldSpec(
            equipmentClass = "UTILITY_SOURCE",
            fieldId = "availableFaultCurrentSlg",
            checklistRequirement = "Available line-to-ground fault current at the supply point.",
            dataBlockSlot = "— (no slot populated)",
            populationSource = "Not modeled at the grid node (z0OverZ1 exists on TransformerEntity but no utility-side zero-sequence source).",
            auditStatus = LabelFieldAuditStatus.PENDING_DATA_MODEL,
            notes = "Same dependency as availableFaultCurrent3Ph."
        ),
        // ---- TRANSFORMER ----
        LabelFieldSpec(
            equipmentClass = "TRANSFORMER",
            fieldId = "kvaRating",
            checklistRequirement = "kVA/MVA rating.",
            dataBlockSlot = "SldDataBlock.ratedKva",
            populationSource = "TransformerResult.selectedSize ?: TransformerEntity.kVA",
            auditStatus = LabelFieldAuditStatus.RENDERED,
            notes = "Rendered together with txType, e.g. \"1000 kVA (Dry)\"."
        ),
        LabelFieldSpec(
            equipmentClass = "TRANSFORMER",
            fieldId = "voltageRatio",
            checklistRequirement = "Primary/secondary voltage ratio.",
            dataBlockSlot = "SldDataBlock.voltage (\"primary / 400 V\")",
            populationSource = "Primary from grid context; secondary currently HARDCODED to 400 V (ElectricalNetworkGraphBuilder secondaryVoltage).",
            auditStatus = LabelFieldAuditStatus.RENDERED,
            notes = "Caveat: TransformerEntity.voltage is a single LV value; a primary-voltage field would make the ratio fully data-driven."
        ),
        LabelFieldSpec(
            equipmentClass = "TRANSFORMER",
            fieldId = "percentImpedance",
            checklistRequirement = "%Z impedance.",
            dataBlockSlot = "SldDataBlock.impedancePct",
            populationSource = "TransformerEntity.percentZ",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "TRANSFORMER",
            fieldId = "windingConnection",
            checklistRequirement = "Winding connection (delta-wye etc., e.g. Dyn11).",
            dataBlockSlot = "SldDataBlock.windingConnection",
            populationSource = "TransformerEntity.vectorGroup",
            auditStatus = LabelFieldAuditStatus.RENDERED,
            notes = "Slot + wiring added in this §1.2 pass — the data always existed in the entity."
        ),
        LabelFieldSpec(
            equipmentClass = "TRANSFORMER",
            fieldId = "groundingMethod",
            checklistRequirement = "Grounding method (solid/resistance/ungrounded neutral).",
            dataBlockSlot = "SldDataBlock.groundingMethod — NOT ADDED (no data source; no dead slots per §1.1 policy)",
            populationSource = "Not modeled. EarthingEntity exists separately for the earthing system, but no neutral-grounding-method field on the transformer.",
            auditStatus = LabelFieldAuditStatus.PENDING_DATA_MODEL,
            notes = "Vector group implies LV star point but not the earthing arrangement; needs an explicit entity field."
        ),
        LabelFieldSpec(
            equipmentClass = "TRANSFORMER",
            fieldId = "fullLoadCurrent",
            checklistRequirement = "Full-load / design current (supports busbar sizing).",
            dataBlockSlot = "SldDataBlock.ibAmp",
            populationSource = "TransformerResult.flc, fallback computed from kVA / (√3 × 400 V)",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        // ---- SWITCHGEAR / PROTECTIVE DEVICE (breaker overlays on feeders) ----
        LabelFieldSpec(
            equipmentClass = "SWITCHGEAR_DEVICE",
            fieldId = "deviceType",
            checklistRequirement = "Device type (MCCB/ACB).",
            dataBlockSlot = "SldDataBlock.deviceTypeAndRating (prefix)",
            populationSource = "CircuitEntity.overrideDeviceType, resolved against the device catalog",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "SWITCHGEAR_DEVICE",
            fieldId = "tripRating",
            checklistRequirement = "Trip rating (In).",
            dataBlockSlot = "SldDataBlock.deviceTypeAndRating (suffix)",
            populationSource = "CircuitEntity.overrideDeviceSize or sizing-resolved deviceSize_A",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "SWITCHGEAR_DEVICE",
            fieldId = "interruptingRatingIcu",
            checklistRequirement = "Interrupting rating (Icu, kA) — must exceed available fault duty.",
            dataBlockSlot = "SldDataBlock.interruptingRatingKa",
            populationSource = "SizingResult.deviceBreakingKA (breaker.icu_kA from the device catalog, fallback standard by device type)",
            auditStatus = LabelFieldAuditStatus.RENDERED,
            notes = "Slot + wiring added in this §1.2 pass — the sizing pipeline always carried Icu for the Icu ≥ Isc PASS/FAIL check but never rendered it."
        ),
        LabelFieldSpec(
            equipmentClass = "SWITCHGEAR_DEVICE",
            fieldId = "frameSize",
            checklistRequirement = "Frame size of the protective device.",
            dataBlockSlot = "— (no slot populated)",
            populationSource = "Not modeled: device catalog entries carry icu_kA but no frame designation.",
            auditStatus = LabelFieldAuditStatus.PENDING_DATA_MODEL,
            notes = "Often inferable from trip rating + manufacturer catalog; needs a catalog field first."
        ),
        // ---- BUSBAR / PANEL ----
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "phaseConfiguration",
            checklistRequirement = "Phase configuration (3Ph/1Ph).",
            dataBlockSlot = "SldDataBlock.voltage (\"400 V (3Ph)\" / \"230 V (1Ph)\")",
            populationSource = "CircuitEntity.phases",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "busbarCurrentRating",
            checklistRequirement = "Busbar current rating (A).",
            dataBlockSlot = "— (no dedicated slot; panel dataBlock.ibAmp carries the circuit design current Ib, not a busbar rating)",
            populationSource = "Not modeled: no busbar-rating field on the panel; Roadmap §2.3 port model + busway sizing (icwKA) are the natural future sources.",
            auditStatus = LabelFieldAuditStatus.PENDING_DATA_MODEL,
            notes = "Do not confuse with design current Ib which is already rendered."
        ),
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "connectedLoadKva",
            checklistRequirement = "Connected / capacity kVA of the board.",
            dataBlockSlot = "— (panel nodes render deviceTypeAndRating as ratingText today; changing ratedKva would alter layout metrics)",
            populationSource = "CircuitEntity.loadKVA exists (DATA_AVAILABLE) but is deliberately not bound to ratedKva to avoid a visual/layout shift outside an approved visual pass.",
            auditStatus = LabelFieldAuditStatus.DATA_AVAILABLE_NOT_RENDERED,
            notes = "Candidate for the §3.2 toggleable detail levels so no default-view clutter or layout metrics change."
        ),
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "shortCircuitDuty",
            checklistRequirement = "Fault duty at the board (kA).",
            dataBlockSlot = "SldDataBlock.faultLevelKa",
            populationSource = "ShortCircuitResult.iscKA per circuit",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "designCurrentIb",
            checklistRequirement = "Design current (Ib) of the board feeder.",
            dataBlockSlot = "SldDataBlock.ibAmp",
            populationSource = "SizingResult.Ib_A",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "voltageDrop",
            checklistRequirement = "Voltage drop (circuit and cumulative).",
            dataBlockSlot = "SldDataBlock.voltageDropPct / cumulativeVdPct",
            populationSource = "SizingResult.vdPct / cumulativeVDpct",
            auditStatus = LabelFieldAuditStatus.RENDERED
        ),
        LabelFieldSpec(
            equipmentClass = "BUSBAR_PANEL",
            fieldId = "feederCable",
            checklistRequirement = "Feeder cable size/type.",
            dataBlockSlot = "SldDataBlock.cableDescription (+ parallelRuns, lengthM, izAmp)",
            populationSource = "SizingResult.cableSize_mm2 / CircuitEntity.cableType",
            auditStatus = LabelFieldAuditStatus.RENDERED
        )
    )

    /** All checklist specs for one equipment class. */
    fun fieldsFor(equipmentClass: String): List<LabelFieldSpec> =
        labelFields.filter { it.equipmentClass == equipmentClass }
}