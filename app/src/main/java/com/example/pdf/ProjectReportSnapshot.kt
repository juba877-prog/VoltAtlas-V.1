package com.example.pdf

import com.example.data.*

data class ProjectReportSnapshot(
    val project: ProjectEntity,
    val circuits: List<CircuitEntity>,
    val transformers: List<TransformerEntity>,
    val sizingResults: Map<Int, com.example.engine.SizingResult>,
    val shortCircuitResults: Map<Int, com.example.engine.ShortCircuitResult>,
    val conduitResults: Map<Int, com.example.engine.ConduitFillResult>,
    val conduitProcurement: List<com.example.engine.ConduitProcurementGroup>,
    val adsResults: Map<Int, com.example.engine.AdsResult> = emptyMap(),
    /**
     * ECT-EE-06 (D5): the authoritative per-circuit protection verdicts.
     *
     * Added with a default so every existing construction site stays source-compatible.
     * When a circuit has a sizing result but no entry here, the report must resolve that
     * circuit to INCOMPLETE — never to PASS — because protection was not evaluated.
     */
    val protectionResults: Map<Int, com.example.engine.ProtectionResult> = emptyMap(),
    val transformerResults: Map<String, com.example.engine.TransformerResult> = emptyMap(),
    val boq: List<com.example.engine.BoqItem> = emptyList(),
    val procurement: List<com.example.engine.ProcurementItem> = emptyList(),
    val drums: List<com.example.engine.DrumAllocation> = emptyList(),
    val generatorResults: Map<String, com.example.engine.GeneratorResult> = emptyMap(),
    val atsResults: Map<String, com.example.engine.AtsResult> = emptyMap(),
    val upsResults: Map<String, com.example.engine.UpsResult> = emptyMap(),
    val pfCorrectionResults: List<com.example.engine.PfCorrectionResult> = emptyList(),
    val earthingResult: com.example.engine.EarthingResult? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val options: ReportOptions
)

data class ReportOptions(
    val isCompleteProjectReport: Boolean = false,
    val includeCalculationDetails: Boolean = true,
    val includeWarningsOpenItems: Boolean = true,
    val includeProvenance: Boolean = true,
    val includeDocumentControl: Boolean = true,
    val includeEmptySections: Boolean = false,
    val selectedSections: List<String> = emptyList(),
    val isPortrait: Boolean = true,
    val projectName: String = "",
    val engineer: String = "",
    val contractorCompany: String = "",
    val consultant: String = "",
    val owner: String = "",
    val date: String = "",
    val revision: String = "A"
)
