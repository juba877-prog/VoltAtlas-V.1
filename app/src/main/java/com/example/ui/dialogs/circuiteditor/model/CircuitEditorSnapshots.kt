package com.example.ui.dialogs.circuiteditor.model

import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.catalog.BuswayCatalogEntry

/**
 * Read-only snapshots passed from the caller/coordinator into the Circuit Editor.
 *
 * These snapshots decouple the pure editor logic from database DAOs, ViewModel
 * instances, and asynchronous Flows.
 */
data class CircuitEditorPreferencesSnapshot(
    val defaultVoltage3Phase: Int = 400,
    val defaultMaxVdPower: Double = 5.0,
    val defaultConductor: String = "Cu",
    val defaultInsulation: String = "XLPE",
    val defaultCoreConfig: String = "4C"
)

data class TransformerSnapshot(
    val transformerId: String,
    val ratingKVA: Double = 0.0
)

data class GeneratorSnapshot(
    val generatorId: String
)

data class AtsSnapshot(
    val atsId: String
)

data class UpsSnapshot(
    val upsId: String
)

data class CableCatalogSnapshot(
    val manufacturers: List<String> = emptyList(),
    val cableTypes: List<CableType> = emptyList(),
    val cableCatalog: List<CableCatalogEntry> = emptyList(),
    val busways: List<BuswayCatalogEntry> = emptyList()
)
