package com.example.ui

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import com.example.engine.BoqItem
import com.example.engine.DrumItem
import com.example.engine.DrumAllocation
import com.example.engine.ProcurementItem
import com.example.engine.PanelScheduleResult

import com.example.engine.GeneratorResult
import com.example.engine.AtsResult
import com.example.engine.UpsResult
import com.example.engine.PfCorrectionResult


import com.example.engine.AdsResult
import com.example.engine.CoordinationResult
import com.example.engine.ConduitFillResult
import com.example.engine.ConduitProcurementGroup

data class ProjectValidationSummary(
    val totalFailures: Int = 0,
    val totalReviewsRequired: Int = 0,
    val totalWarnings: Int = 0,
    val overallStatus: String = "100% COMPLIANT / PASS",
    val openIssues: List<com.example.sld.SldValidationIssue> = emptyList()
)

sealed interface UiState {
    object Loading : UiState
    data class Error(val message: String) : UiState
    data class Success(
        val circuits: List<CircuitEntity>,
        val transformers: List<TransformerEntity>,
        val sizingResults: Map<Int, SizingResult>,
        val transformerResults: Map<String, TransformerResult>,
        val boq: List<BoqItem>,
        val drums: List<DrumAllocation>,
        val procurement: List<ProcurementItem>,
        val panels: List<PanelScheduleResult>,
        val generatorResults: Map<String, GeneratorResult> = emptyMap(),
        val generators: List<com.example.data.GeneratorEntity> = emptyList(),
        val atsResults: Map<String, AtsResult> = emptyMap(),
        val atsEntity: com.example.data.AtsEntity? = null,
        val atsList: List<com.example.data.AtsEntity> = emptyList(),
        val upsResults: Map<String, UpsResult> = emptyMap(),
        val upsList: List<com.example.data.UpsEntity> = emptyList(),
        val pfCorrectionResults: List<PfCorrectionResult>,

        // Protection / Short Circuit
        val shortCircuitResults: Map<Int, com.example.engine.ShortCircuitResult> = emptyMap(),
        val adsResults: Map<Int, AdsResult> = emptyMap(),
        val protectionResults: Map<Int, com.example.engine.ProtectionResult> = emptyMap(),
        val coordinationResults: List<CoordinationResult> = emptyList(),
        val earthingResult: com.example.engine.EarthingResult? = null,
        val earthingEntity: com.example.data.EarthingEntity? = null,

        // Conduits Module
        val conduitResults: Map<Int, ConduitFillResult> = emptyMap(),
        val conduitProcurement: List<ConduitProcurementGroup> = emptyList(),

        // Network chain for downstream engines
        val resolvedCircuits: List<CircuitEntity> = emptyList(),
        val circuitParents: Map<Int, Int> = emptyMap(), // child circuit id -> parent circuit id
        val drumAllocations: List<DrumAllocation> = emptyList(),
        val validationSummary: ProjectValidationSummary = ProjectValidationSummary()
    ) : UiState
}

