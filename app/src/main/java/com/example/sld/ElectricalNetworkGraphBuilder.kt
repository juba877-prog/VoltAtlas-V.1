package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.data.GeneratorEntity
import com.example.data.AtsEntity
import com.example.data.UpsEntity
import com.example.data.BusTieEntity
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import com.example.engine.ShortCircuitResult
import com.example.engine.ConduitFillResult
import com.example.engine.GeneratorResult
import com.example.engine.AtsResult
import com.example.engine.UpsResult
import com.example.engine.PfCorrectionResult

object ElectricalNetworkGraphBuilder {

    /** Node classes treated as switchboards for the Phase 2A load roll-up. */
    private val BOARD_ROLLUP_NODE_TYPES = setOf(
        SldNodeType.MDB, SldNodeType.SMDB, SldNodeType.DB, SldNodeType.MCC
    )

    fun buildGraph(
        activeProjectId: String,
        transformers: List<TransformerEntity>,
        circuits: List<CircuitEntity>,
        resolvedCircuits: List<CircuitEntity> = emptyList(),
        circuitParents: Map<Int, Int> = emptyMap(),
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        transformerResults: Map<String, TransformerResult> = emptyMap(),
        shortCircuitResults: Map<Int, ShortCircuitResult> = emptyMap(),
        conduitResults: Map<Int, ConduitFillResult> = emptyMap(),
        pfCorrectionResults: List<PfCorrectionResult> = emptyList(),
        generators: List<GeneratorEntity>? = null,
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        atsList: List<AtsEntity>? = null,
        atsResults: Map<String, AtsResult> = emptyMap(),
        upsList: List<UpsEntity>? = null,
        upsResults: Map<String, UpsResult> = emptyMap(),
        protectionResults: Map<Int, com.example.engine.ProtectionResult> = emptyMap(),
        adsResults: Map<Int, com.example.engine.AdsResult> = emptyMap(),
        busTies: List<BusTieEntity>? = null
    ): ElectricalNetworkGraph {
        val nodes = mutableListOf<SldNode>()
        val edges = mutableListOf<SldEdge>()
        val unconnected = mutableListOf<SldNode>()
        val validationIssues = mutableListOf<SldValidationIssue>()

        // Effective project filtering
        val effectiveProjectId = if (activeProjectId.isNotBlank()) activeProjectId else "DEFAULT_PROJECT"

        val projTransformers = transformers.filter { it.projectId == effectiveProjectId || (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT") }
        val projCircuits = circuits.filter { it.projectId == effectiveProjectId || (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT") }

        val crossProjectCircuits = circuits.filter { it.projectId != effectiveProjectId && it.projectId != "DEFAULT_PROJECT" }
        crossProjectCircuits.forEach { orphanCirc ->
            validationIssues.add(
                SldValidationIssue(
                    affectedNodeId = "circ_${orphanCirc.id}",
                    componentName = orphanCirc.circuitName,
                    failedCondition = "Cross-Project Isolation Violation: Circuit belongs to project '${orphanCirc.projectId}'",
                    issueCode = "CROSS_PROJECT_REF",
                    severity = SldValidationStatus.INVALID,
                    recommendedCorrection = "Ensure all active elements match current active project ID '$effectiveProjectId'"
                )
            )
        }

        // 1. Process Transformers & Grid Sources
        val txMap = projTransformers.associateBy { it.transformerId.lowercase() }

        if (projTransformers.isEmpty()) {
            val defaultGridId = "grid_default"
            nodes.add(
                SldNode(
                    id = defaultGridId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.UTILITY_SOURCE,
                    // Golden rule: never fabricate a voltage — the project has no
                    // transformer, therefore no known supply voltage to display.
                    displayName = "Utility Grid",
                    voltage = null,
                    dataBlock = SldDataBlock(
                        statusText = "UTILITY GRID INCOMER"
                    ),
                    status = SldValidationStatus.VALID,
                    provenance = SldProvenance.APPROVED_STANDARD,
                    targetTab = 2
                )
            )
        } else {
            projTransformers.forEach { tx ->
                val txNodeId = "tx_${tx.transformerId}"
                val gridNodeId = "grid_${tx.transformerId}"
                // G5: the grid (utility) side is the REAL primary voltage column. `tx.voltage`
                // is the LV secondary (used for FLC/fault math). Never fabricate a supply
                // voltage — when the primary is unset the grid node carries none.
                val primaryVoltageStr = tx.primaryVoltageV?.takeIf { it > 0 }?.let { pv ->
                    if (pv >= 1000) {
                        val kv = pv / 1000.0
                        if (kv == kv.toLong().toDouble()) "${kv.toLong()} kV" else "$kv kV"
                    } else {
                        "$pv V"
                    }
                }
                val gridDisplayName = if (primaryVoltageStr != null) "Utility Grid ($primaryVoltageStr)" else "Utility Grid"

                nodes.add(
                    SldNode(
                        id = gridNodeId,
                        projectId = effectiveProjectId,
                        type = SldNodeType.UTILITY_SOURCE,
                        displayName = gridDisplayName,
                        voltage = primaryVoltageStr,
                        dataBlock = SldDataBlock(
                            voltage = primaryVoltageStr,
                            statusText = "GRID SOURCE INCOMER",
                            // G4: surface the utility S_kQ when the engineer enters it.
                            gridFaultMVA = tx.gridFaultMVA?.takeIf { it > 0.0 }?.let { "%.1f MVA".format(it) },
                            transformerPrimaryVoltage = primaryVoltageStr
                        ),
                        status = SldValidationStatus.VALID,
                        provenance = SldProvenance.APPROVED_STANDARD,
                        targetTab = 2,
                        rawTransformerId = tx.transformerId
                    )
                )

                val txRes = transformerResults[tx.transformerId]
                val ratedKvaVal = txRes?.selectedSize?.takeIf { it > 0.0 } ?: tx.kVA
                val ratedKvaStr = if (ratedKvaVal > 0) "${ratedKvaVal.toInt()} kVA" else "DATA REQUIRED"
                // G5: the LV secondary voltage is the real `tx.voltage` column.
                // P0-4: an out-of-range/invalid secondary voltage is an explicit
                // DATA REQUIRED state — never a fabricated 400 V basis for FLC.
                val realSecondaryVoltage: Double? = tx.voltage.toDouble().takeIf { it in 100.0..1000.0 }
                val fallbackFlc = realSecondaryVoltage?.let { (ratedKvaVal * 1000) / (Math.sqrt(3.0) * it) }
                val ibAmpStr = txRes?.flc?.let { "%.0f A".format(it) }
                    ?: fallbackFlc?.let { "%.0f A".format(it) }
                    ?: "DATA REQUIRED"
                val loadingStr = txRes?.loadingPct?.let { "%.1f%%".format(it) } ?: ""
                val iscKaStr = txRes?.isc3Phase?.let { "%.2f kA".format(it) } ?: ""
                val secondaryVoltageStr = realSecondaryVoltage?.let { v ->
                    if (v == v.toLong().toDouble()) "${v.toLong()} V" else "$v V"
                } ?: "DATA REQUIRED"
                val txCombinedVoltageStr = if (primaryVoltageStr != null) "$primaryVoltageStr / $secondaryVoltageStr" else secondaryVoltageStr

                nodes.add(
                    SldNode(
                        id = txNodeId,
                        projectId = effectiveProjectId,
                        type = SldNodeType.TRANSFORMER,
                        displayName = "Tx: ${tx.transformerId}",
                        parentSourceId = gridNodeId,
                        voltage = txCombinedVoltageStr,
                        dataBlock = SldDataBlock(
                            ratedKva = "$ratedKvaStr (${tx.txType})",
                            voltage = txCombinedVoltageStr,
                            impedancePct = "${tx.percentZ}%",
                            windingConnection = tx.vectorGroup,
                            loadingPct = loadingStr,
                            isc3PhaseKa = iscKaStr,
                            ibAmp = ibAmpStr,
                            transformerZtMOhm = txRes?.zt?.let { "%.1f mΩ".format(it) },
                            transformerIsc1PhaseKa = txRes?.isc1Phase?.let { "%.2f kA".format(it) },
                            transformerPrimaryVoltage = primaryVoltageStr,
                            gridFaultMVA = tx.gridFaultMVA?.takeIf { it > 0.0 }?.let { "%.1f MVA".format(it) },
                            statusText = txRes?.status ?: "VERIFIED"
                        ),
                        status = if (txRes != null) SldValidationStatus.VALID else SldValidationStatus.VALID,
                        provenance = SldProvenance.PROJECT_INPUT,
                        targetTab = 2,
                        rawTransformerId = tx.transformerId
                    )
                )

                edges.add(
                    SldEdge(
                        id = "edge_${gridNodeId}_${txNodeId}",
                        fromNodeId = gridNodeId,
                        toNodeId = txNodeId,
                        type = SldEdgeType.SOURCE_CONNECTION
                    )
                )
            }
        }

        // 2. Multi-Instance Generator Processing
        val projGenerators = generators?.filter {
            it.projectId == effectiveProjectId || (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT")
        } ?: emptyList()

        val effectiveGenResults: Map<String, GeneratorResult> = generatorResults

        // P0-4: derived genset voltages are reused by the sync-bus node (single derivation
        // point — the sync bus may not invent its own voltage).
        val genVoltageByGenId = mutableMapOf<String, Int?>()

        projGenerators.forEach { gen ->
            val genId = gen.generatorId.ifBlank { "1" }
            val genNodeId = "gen_$genId"
            val genRes = effectiveGenResults[genId]
            val genRatedKva = genRes?.finalSize?.takeIf { it > 0.0 } ?: gen.manualSizeKVA ?: 0.0
            val genRatedKvaStr = if (genRatedKva > 0.0) "${genRatedKva.toInt()} kVA" else "DATA REQUIRED"
            // P0-4 (deviation-aware): GeneratorEntity has NO voltage column, so no migration is
            // introduced. The authoritative voltage for a genset is the system voltage of the ATS
            // it feeds (AtsEntity.systemVoltage — the SAME relationship used for frequency).
            // No linked ATS / unresolved disagreement ⇒ DATA REQUIRED, never a silent 400 V.
            val linkedAtsVoltages = atsList
                ?.filter { it.projectId == effectiveProjectId || it.projectId == "DEFAULT_PROJECT" }
                ?.filter { it.emergencySourceId?.uppercase() == genId.uppercase() }
                ?.mapNotNull { it.systemVoltage.takeIf { v -> v > 0 } }
                ?.distinct()
                ?: emptyList()
            val genVoltageV: Int? = linkedAtsVoltages.singleOrNull()
            genVoltageByGenId[gen.generatorId] = genVoltageV
            val genVoltageStr = genVoltageV?.let { "$it V" } ?: "DATA REQUIRED"
            val genIbAmp = genVoltageV?.let {
                "%.0f A".format((genRatedKva * 1000) / (Math.sqrt(3.0) * it.toDouble()))
            }
            val genLoadingPct = genRes?.loadingPct?.let { "%.1f%%".format(it) } ?: ""
            val genName = gen.generatorName.ifBlank { "Generator $genId" }
            val genStatusText = if (genRes?.status?.isNotBlank() == true) genRes.status else "EMERGENCY GENERATOR"
            // Real output frequency: derive ONLY from project-scoped ATS instances whose
            // emergency source is this generator (AtsEntity.systemFrequency, stored column).
            // P0-7: the former `?: atsList?.firstOrNull()` tail could stamp a foreign-project
            // frequency — removed; no linked ATS ⇒ frequency stays absent.
            val genFreq = atsList
                ?.filter { it.projectId == effectiveProjectId || it.projectId == "DEFAULT_PROJECT" }
                ?.firstOrNull { it.emergencySourceId?.uppercase() == genId.uppercase() }
                ?.systemFrequency

            nodes.add(
                SldNode(
                    id = genNodeId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.GENERATOR,
                    displayName = "$genName ($genRatedKvaStr)",
                    voltage = genVoltageStr,
                    dataBlock = SldDataBlock(
                        ratedKva = genRatedKvaStr,
                        voltage = genVoltageStr,
                        ibAmp = genIbAmp,
                        loadingPct = genLoadingPct,
                        generatorMotorStartingKva = genRes?.motorStartingKVA?.takeIf { it > 0.0 }?.let { "%.0f kVA".format(it) },
                        generatorStepLoadKva = genRes?.stepLoadAtStart?.takeIf { it > 0.0 }?.let { "%.0f kVA".format(it) },
                        voltageFrequencyHz = genFreq?.let { "$it Hz" },
                        statusText = genStatusText
                    ),
                    status = SldValidationStatus.VALID,
                    provenance = SldProvenance.PROJECT_INPUT,
                    targetTab = 3
                )
            )
        }

        // 2.2b Generator Synchronizing / Paralleling Bus Synthesis (Roadmap Phase 2 §2.1).
        // STRICT ID RESOLUTION: generators are grouped exclusively by their explicit
        // syncBusId (preferred) or parallelingGroupId columns. Only groups of two or
        // more generators warrant a synchronizing bus — a single genset runs standalone.
        // The resulting SYNC_BUS node sits between Tier 0 (sources) and Tier 1 (boards):
        // gen_* --GEN_SYNC_CONNECTION--> sync_<busId> --ATS_EMERGENCY_PATH--> ats_*.
        val syncBusNodeByGeneratorNodeId = mutableMapOf<String, String>()
        run {
            val syncGroups = linkedMapOf<String, MutableList<GeneratorEntity>>()
            projGenerators.forEach { gen ->
                val groupKey = gen.parallelingGroupKey() ?: return@forEach
                syncGroups.getOrPut(groupKey) { mutableListOf() }.add(gen)
            }
            syncGroups.forEach { (groupKey, groupGens) ->
                if (groupGens.size < GeneratorEntity.MIN_PARALLELING_GROUP_SIZE) return@forEach
                val syncNodeId = "sync_$groupKey"
                val groupKva = groupGens.sumOf { (it.manualSizeKVA ?: 0.0) }
                val groupKvaStr = if (groupKva > 0.0) " %.0f kVA total".format(groupKva) else ""
                // P0-4: sync-bus voltage follows its member gensets (which follow their ATS).
                // Any unknown member voltage or disagreement ⇒ DATA REQUIRED, never "400 V".
                val memberVoltages = groupGens.map { genVoltageByGenId[it.generatorId] }
                val syncVoltageStr = if (memberVoltages.isNotEmpty() &&
                    memberVoltages.all { it != null } && memberVoltages.distinct().size == 1
                ) "${memberVoltages.first()} V" else "DATA REQUIRED"
                nodes.add(
                    SldNode(
                        id = syncNodeId,
                        projectId = effectiveProjectId,
                        type = SldNodeType.SYNC_BUS,
                        displayName = "Sync Bus $groupKey",
                        voltage = syncVoltageStr,
                        dataBlock = SldDataBlock(
                            ratedKva = if (groupKva > 0.0) "${groupKva.toInt()} kVA" else null,
                            voltage = syncVoltageStr,
                            statusText = "SYNCHRONIZING BUS (${groupGens.size} GENERATORS)$groupKvaStr"
                        ),
                        status = SldValidationStatus.VALID,
                        provenance = SldProvenance.PROJECT_INPUT,
                        targetTab = 3
                    )
                )
                groupGens.forEach { groupGen ->
                    val genNodeId = "gen_${groupGen.generatorId}"
                    syncBusNodeByGeneratorNodeId[genNodeId] = syncNodeId
                    edges.add(
                        SldEdge(
                            id = "edge_${genNodeId}_$syncNodeId",
                            fromNodeId = genNodeId,
                            toNodeId = syncNodeId,
                            type = SldEdgeType.GEN_SYNC_CONNECTION,
                            label = "SYNC"
                        )
                    )
                }
            }
        }

        // Multi-Instance ATS Processing
        val projAtsList = atsList?.filter {
            it.projectId == effectiveProjectId ||
                (
                    it.projectId == "DEFAULT_PROJECT" &&
                        effectiveProjectId != "DEFAULT_PROJECT"
                )
        } ?: emptyList()

        val effectiveAtsResults: Map<String, AtsResult> = atsResults

        projAtsList.forEach { ats ->
            val atsId = ats.atsId.ifBlank { "1" }
            val atsNodeId = "ats_$atsId"
            val atsRes = effectiveAtsResults[atsId]
            val atsOutputBoardId = ats.outputBoardId
            val atsName = ats.atsName.ifBlank { "ATS-$atsId" }

            // Deterministic Normal Source Resolution
            val rawNormalSrc = ats.normalSourceId?.trim().orEmpty()
            var normalSourceNodeId: String? = null
            var normalSourceErrorIssue: SldValidationIssue? = null

            if (rawNormalSrc.isNotBlank()) {
                // Step 1: Exact numeric circuit ID match
                val numericCircuitId = rawNormalSrc.toIntOrNull()
                val matchedCircuitById = numericCircuitId?.let { id ->
                    projCircuits.firstOrNull { it.id == id }
                }

                if (matchedCircuitById != null) {
                    normalSourceNodeId = "circ_${matchedCircuitById.id}"
                } else {
                    // Step 2: Exact normalized circuit name match
                    val circuitNameMatches = projCircuits.filter {
                        it.circuitName.trim().equals(rawNormalSrc, ignoreCase = true)
                    }

                    if (circuitNameMatches.size == 1) {
                        normalSourceNodeId = "circ_${circuitNameMatches.single().id}"
                    } else if (circuitNameMatches.size > 1) {
                        val conflictingIds = circuitNameMatches.map { it.id }.joinToString()
                        normalSourceErrorIssue = SldValidationIssue(
                            affectedNodeId = atsNodeId,
                            componentName = atsName,
                            failedCondition = "Ambiguous ATS Normal Source: Multiple circuits named '$rawNormalSrc' exist (IDs: $conflictingIds)",
                            issueCode = "ATS_AMBIGUOUS_NORMAL_SOURCE",
                            severity = SldValidationStatus.INVALID,
                            recommendedCorrection = "Ensure circuit names are unique or re-select the ATS Normal Source"
                        )
                    } else {
                        // Step 3: Exact Transformer ID match
                        val txIdMatches = projTransformers.filter {
                            it.transformerId.trim().equals(rawNormalSrc, ignoreCase = true)
                        }

                        if (txIdMatches.size == 1) {
                            normalSourceNodeId = "tx_${txIdMatches.single().transformerId}"
                        } else if (txIdMatches.size > 1) {
                            val conflictingIds = txIdMatches.map { it.transformerId }.joinToString()
                            normalSourceErrorIssue = SldValidationIssue(
                                affectedNodeId = atsNodeId,
                                componentName = atsName,
                                failedCondition = "Ambiguous ATS Normal Source: Multiple transformers with ID '$rawNormalSrc' exist (IDs: $conflictingIds)",
                                issueCode = "ATS_AMBIGUOUS_NORMAL_SOURCE",
                                severity = SldValidationStatus.INVALID,
                                recommendedCorrection = "Ensure transformer IDs are unique or re-select the ATS Normal Source"
                            )
                        } else {
                            // Step 4: Exact Transformer Name match
                            val txNameMatches = projTransformers.filter {
                                it.transformerName.trim().equals(rawNormalSrc, ignoreCase = true)
                            }

                            if (txNameMatches.size == 1) {
                                normalSourceNodeId = "tx_${txNameMatches.single().transformerId}"
                            } else if (txNameMatches.size > 1) {
                                val conflictingIds = txNameMatches.map { it.transformerId }.joinToString()
                                normalSourceErrorIssue = SldValidationIssue(
                                    affectedNodeId = atsNodeId,
                                    componentName = atsName,
                                    failedCondition = "Ambiguous ATS Normal Source: Multiple transformers named '$rawNormalSrc' exist (IDs: $conflictingIds)",
                                    issueCode = "ATS_AMBIGUOUS_NORMAL_SOURCE",
                                    severity = SldValidationStatus.INVALID,
                                    recommendedCorrection = "Ensure transformer names are unique or re-select the ATS Normal Source"
                                )
                            } else if (rawNormalSrc.lowercase() in setOf("transformer", "tx", "grid", "utility", "source", "main")) {
                                // Step 5: Generic Source Keywords
                                if (projTransformers.size == 1) {
                                    normalSourceNodeId = "tx_${projTransformers.single().transformerId}"
                                } else if (projTransformers.size > 1) {
                                    normalSourceErrorIssue = SldValidationIssue(
                                        affectedNodeId = atsNodeId,
                                        componentName = atsName,
                                        failedCondition = "Ambiguous ATS Normal Source: Generic keyword '$rawNormalSrc' used but multiple transformers exist in project",
                                        issueCode = "ATS_AMBIGUOUS_NORMAL_SOURCE",
                                        severity = SldValidationStatus.INVALID,
                                        recommendedCorrection = "Explicitly select specific transformer ID for ATS normal source"
                                    )
                                } else {
                                    val utilSource = nodes.find { it.type == SldNodeType.UTILITY_SOURCE }
                                    if (utilSource != null) {
                                        normalSourceNodeId = utilSource.id
                                    } else {
                                        normalSourceErrorIssue = SldValidationIssue(
                                            affectedNodeId = atsNodeId,
                                            componentName = atsName,
                                            failedCondition = "ATS Normal Power Source '$rawNormalSrc' not found in project network",
                                            issueCode = "ATS_NORMAL_SOURCE_NOT_FOUND",
                                            severity = SldValidationStatus.INVALID,
                                            recommendedCorrection = "Configure transformer or utility source in project"
                                        )
                                    }
                                }
                            } else {
                                // Step 6: Non-matching named source
                                normalSourceErrorIssue = SldValidationIssue(
                                    affectedNodeId = atsNodeId,
                                    componentName = atsName,
                                    failedCondition = "ATS Normal Power Source '$rawNormalSrc' not found in project network",
                                    issueCode = "ATS_NORMAL_SOURCE_NOT_FOUND",
                                    severity = SldValidationStatus.INVALID,
                                    recommendedCorrection = "Verify the ATS Normal Source selection in Power Systems"
                                )
                            }
                        }
                    }
                }
            } else {
                // Unassigned normal source fallback: P0-7 — require a UNIQUE upstream MDB
                // candidate; multiple MDB-looking circuits are ambiguous (no instance #1),
                // falling through to the single-transformer case or the explicit
                // ATS_NORMAL_SOURCE_REQUIRED state handled downstream.
                // The ATS's own downstream board is excluded (fedFrom == this ATS name) —
                // an ATS cannot be normal-fed from the board it supplies (circular path),
                // same intent as the assignedAtsId exclusion below.
                val mdbCandidates = projCircuits.filter { circ ->
                    (atsOutputBoardId == null || circ.id != atsOutputBoardId) &&
                    circ.assignedAtsId != atsId &&
                    !circ.fedFrom.trim().equals(atsName, ignoreCase = true) &&
                    ((circ.panelType != null && circ.panelType.lowercase().contains("mdb")) ||
                    circ.circuitName.lowercase().contains("mdb") ||
                    circ.fedFrom.trim().lowercase().startsWith("trans") ||
                    circ.fedFrom.trim().lowercase().startsWith("tx"))
                }
                when {
                    mdbCandidates.size == 1 -> {
                        normalSourceNodeId = "circ_${mdbCandidates.single().id}"
                    }
                    mdbCandidates.size > 1 -> {
                        // P0-7: two or more upstream MDB candidates are genuinely ambiguous —
                        // refuse to assert any of them as the normal source.
                        normalSourceErrorIssue = SldValidationIssue(
                            affectedNodeId = atsNodeId,
                            componentName = atsName,
                            failedCondition = "Ambiguous ATS Normal Source: ${mdbCandidates.size} upstream MDB candidates exist (IDs: ${mdbCandidates.joinToString { it.id.toString() }})",
                            issueCode = "AMBIGUOUS_SOURCE_INSTANCE",
                            severity = SldValidationStatus.INVALID,
                            recommendedCorrection = "Select the upstream MDB explicitly via output board or transformer assignment"
                        )
                        normalSourceNodeId = null
                    }
                    projTransformers.size == 1 -> {
                        normalSourceNodeId = "tx_${projTransformers.single().transformerId}"
                    }
                    else -> {
                        normalSourceNodeId = null
                    }
                }
            }

            if (normalSourceErrorIssue != null) {
                validationIssues.add(normalSourceErrorIssue)
            }

            val hasNormalSource = normalSourceNodeId != null && normalSourceErrorIssue == null && (
                projTransformers.any { "tx_${it.transformerId}" == normalSourceNodeId } ||
                projCircuits.any { "circ_${it.id}" == normalSourceNodeId } ||
                nodes.any { it.id == normalSourceNodeId }
            )

            val ratingVal = atsRes?.finalRating?.toInt() ?: ats.manualSizeAmp?.toInt()?.takeIf { it > 0 }
            val atsRating = if (ratingVal != null) "${ratingVal}A ATS" else "ATS"
            val atsIbAmp = atsRes?.governingCurrent?.let { "%.0f A".format(it) } ?: ""

            // Identify Emergency Generator Source for this ATS
            val rawEmergSrc = ats.emergencySourceId?.trim().orEmpty()
            var emergGenNodeId: String? = null
            var emergSourceErrorIssue: SldValidationIssue? = null

            if (rawEmergSrc.isNotBlank()) {
                // Step 1: Exact Generator ID match
                val genIdMatches = projGenerators.filter {
                    it.generatorId.trim().equals(rawEmergSrc, ignoreCase = true)
                }

                if (genIdMatches.size == 1) {
                    emergGenNodeId = "gen_${genIdMatches.single().generatorId}"
                } else if (genIdMatches.size > 1) {
                    val conflictingIds = genIdMatches.map { it.generatorId }.joinToString()
                    emergSourceErrorIssue = SldValidationIssue(
                        affectedNodeId = atsNodeId,
                        componentName = atsName,
                        failedCondition = "Ambiguous ATS Emergency Source: Multiple generators with ID '$rawEmergSrc' exist (IDs: $conflictingIds)",
                        issueCode = "ATS_AMBIGUOUS_EMERGENCY_SOURCE",
                        severity = SldValidationStatus.INVALID,
                        recommendedCorrection = "Ensure generator IDs are unique or re-select the ATS Emergency Source"
                    )
                } else {
                    // Step 2: Exact Generator Name match
                    val genNameMatches = projGenerators.filter {
                        it.generatorName.trim().equals(rawEmergSrc, ignoreCase = true)
                    }

                    if (genNameMatches.size == 1) {
                        emergGenNodeId = "gen_${genNameMatches.single().generatorId}"
                    } else if (genNameMatches.size > 1) {
                        val conflictingIds = genNameMatches.map { it.generatorId }.joinToString()
                        emergSourceErrorIssue = SldValidationIssue(
                            affectedNodeId = atsNodeId,
                            componentName = atsName,
                            failedCondition = "Ambiguous ATS Emergency Source: Multiple generators named '$rawEmergSrc' exist (IDs: $conflictingIds)",
                            issueCode = "ATS_AMBIGUOUS_EMERGENCY_SOURCE",
                            severity = SldValidationStatus.INVALID,
                            recommendedCorrection = "Ensure generator names are unique or re-select the ATS Emergency Source"
                        )
                    } else if (rawEmergSrc.lowercase() in setOf("generator", "gen", "emergency", "genset")) {
                        // Step 3: Generic Generator Keywords
                        if (projGenerators.size == 1) {
                            emergGenNodeId = "gen_${projGenerators.single().generatorId}"
                        } else if (projGenerators.size > 1) {
                            val allGenIds = projGenerators.map { it.generatorId }.joinToString()
                            emergSourceErrorIssue = SldValidationIssue(
                                affectedNodeId = atsNodeId,
                                componentName = atsName,
                                failedCondition = "Ambiguous ATS Emergency Source: Generic keyword '$rawEmergSrc' used but multiple generators exist in project (IDs: $allGenIds)",
                                issueCode = "ATS_AMBIGUOUS_EMERGENCY_SOURCE",
                                severity = SldValidationStatus.INVALID,
                                recommendedCorrection = "Explicitly select specific generator for ATS emergency source"
                            )
                        } else {
                            emergSourceErrorIssue = SldValidationIssue(
                                affectedNodeId = atsNodeId,
                                componentName = atsName,
                                failedCondition = "ATS Emergency Power Source '$rawEmergSrc' not found in project network",
                                issueCode = "ATS_EMERGENCY_SOURCE_NOT_FOUND",
                                severity = SldValidationStatus.INVALID,
                                recommendedCorrection = "Configure a generator in Power Systems"
                            )
                        }
                    } else {
                        // Step 5: Unknown Named Value
                        emergSourceErrorIssue = SldValidationIssue(
                            affectedNodeId = atsNodeId,
                            componentName = atsName,
                            failedCondition = "ATS Emergency Power Source '$rawEmergSrc' not found in project network",
                            issueCode = "ATS_EMERGENCY_SOURCE_NOT_FOUND",
                            severity = SldValidationStatus.INVALID,
                            recommendedCorrection = "Verify the ATS Emergency Source selection in Power Systems"
                        )
                    }
                }
            } else {
                // Step 4: Blank/Unassigned Emergency Source
                if (projGenerators.size == 1) {
                    emergGenNodeId = "gen_${projGenerators.single().generatorId}"
                }
            }

            if (emergSourceErrorIssue != null) {
                validationIssues.add(emergSourceErrorIssue)
            }

            val hasEmergSource = emergGenNodeId != null && emergSourceErrorIssue == null && nodes.any { it.id == emergGenNodeId }

            // Output destination check
            val hasAtsDest = projCircuits.any {
                it.assignedAtsId == atsId ||
                (atsOutputBoardId != null && it.id == atsOutputBoardId) ||
                it.fedFrom.trim().equals(atsName, ignoreCase = true) ||
                it.fedFrom.trim().lowercase().contains("ats")
            }

            val atsStatus = if (!hasNormalSource || !hasEmergSource || !hasAtsDest) {
                SldValidationStatus.DATA_REQUIRED
            } else SldValidationStatus.VALID

            val atsNode = SldNode(
                id = atsNodeId,
                projectId = effectiveProjectId,
                type = SldNodeType.ATS,
                displayName = atsName,
                dataBlock = SldDataBlock(
                    deviceTypeAndRating = atsRating,
                    ibAmp = atsIbAmp,
                    atsRequiredIcwKa = atsRes?.requiredIcw?.takeIf { it > 0.0 }?.let { "%.2f kA".format(it) },
                    atsRequiredIcmKa = atsRes?.requiredIcm?.takeIf { it > 0.0 }?.let { "%.2f kA".format(it) },
                    atsGoverningCurrentA = atsRes?.governingCurrent?.takeIf { it > 0.0 }?.let { "%.0f A".format(it) },
                    atsTransferTimeS = atsRes?.totalTransferToGenerator?.takeIf { it > 0 }?.let { "${it}s" },
                    statusText = atsRes?.status?.ifBlank { "TRANSFER SWITCH" } ?: "TRANSFER SWITCH"
                ),
                status = atsStatus,
                provenance = SldProvenance.PROJECT_INPUT,
                targetTab = 3
            )
            nodes.add(atsNode)

            if (hasNormalSource) {
                edges.add(
                    SldEdge(
                        id = "edge_${normalSourceNodeId}_$atsNodeId",
                        fromNodeId = normalSourceNodeId!!,
                        toNodeId = atsNodeId,
                        type = SldEdgeType.ATS_NORMAL_PATH,
                        label = "NORMAL"
                    )
                )
            } else {
                edges.add(
                    SldEdge(
                        id = "edge_unresolved_normal_$atsNodeId",
                        fromNodeId = "unresolved_normal_$atsNodeId",
                        toNodeId = atsNodeId,
                        type = SldEdgeType.UNRESOLVED_PATH,
                        label = "NO NORMAL SOURCE"
                    )
                )
                if (normalSourceErrorIssue == null) {
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = atsNodeId,
                            componentName = atsName,
                            failedCondition = "ATS Normal Power Source Missing",
                            issueCode = "ATS_NORMAL_SOURCE_REQUIRED",
                            severity = SldValidationStatus.DATA_REQUIRED,
                            recommendedCorrection = "Ensure transformer/utility source is configured and assigned"
                        )
                    )
                }
            }

            if (hasEmergSource) {
                edges.add(
                    SldEdge(
                        id = "edge_${emergGenNodeId}_$atsNodeId",
                        fromNodeId = emergGenNodeId!!,
                        toNodeId = atsNodeId,
                        type = SldEdgeType.ATS_EMERGENCY_PATH,
                        label = "EMERGENCY"
                    )
                )
            } else {
                edges.add(
                    SldEdge(
                        id = "edge_unresolved_emerg_$atsNodeId",
                        fromNodeId = "unresolved_emerg_$atsNodeId",
                        toNodeId = atsNodeId,
                        type = SldEdgeType.UNRESOLVED_PATH,
                        label = "NO EMERGENCY SOURCE"
                    )
                )
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = atsNodeId,
                        componentName = atsName,
                        failedCondition = "ATS Emergency Power Source Missing",
                        issueCode = "ATS_EMERGENCY_SOURCE_REQUIRED",
                        severity = SldValidationStatus.DATA_REQUIRED,
                        recommendedCorrection = "Configure emergency generator source in Power Systems"
                    )
                )
            }

            if (!hasAtsDest) {
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = atsNodeId,
                        componentName = atsName,
                        failedCondition = "ATS Output Destination Missing",
                        issueCode = "ATS_OUTPUT_DESTINATION_REQUIRED",
                        severity = SldValidationStatus.DATA_REQUIRED,
                        recommendedCorrection = "Select a downstream output board on the ATS screen, or assign circuits to this ATS"
                    )
                )
            }
        }

        // 2.4a Emergency-edge re-parenting through synchronizing buses (Roadmap Phase 2 §2.1).
        // When a generator belongs to a sync group, its ATS emergency feed is sourced
        // from the SYNC_BUS, not directly from the genset — downstream panels behind
        // the ATS show the true dual-feed provenance of the paralleled bus.
        if (syncBusNodeByGeneratorNodeId.isNotEmpty()) {
            val rewiredEdges = edges.map { edge ->
                val syncNodeId = if (edge.type == SldEdgeType.ATS_EMERGENCY_PATH) {
                    syncBusNodeByGeneratorNodeId[edge.fromNodeId]
                } else null
                if (syncNodeId != null && nodes.any { it.id == syncNodeId }) {
                    edge.copy(fromNodeId = syncNodeId, id = "edge_${syncNodeId}_${edge.toNodeId}")
                } else edge
            }
            edges.clear()
            edges.addAll(rewiredEdges)
        }

        // 2.4b Bus-Tie Synthesis (Roadmap Phase 2 §2.1 — Main-Tie-Main).
        // STRICT ID RESOLUTION: sourceBoardId/targetBoardId must be explicit
        // CircuitEntity primary keys. No fedFrom string matching is consulted.
        // Unresolvable references raise SldValidationIssue instead of silently
        // mis-linking (replacing the legacy name-heuristic fallback chain).
        val projBusTies = (busTies ?: emptyList()).filter {
            it.projectId == effectiveProjectId ||
                (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT")
        }
        val circuitPrimaryKeyIds = projCircuits.map { it.id.toLong() }.toSet()
        projBusTies.forEach { tie ->
            val srcBoardId = tie.sourceBoardId
            val dstBoardId = tie.targetBoardId
            val srcResolved = srcBoardId != null && srcBoardId in circuitPrimaryKeyIds
            val dstResolved = dstBoardId != null && dstBoardId in circuitPrimaryKeyIds
            if (!srcResolved || !dstResolved) {
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = "tie_${tie.busTieId}",
                        componentName = tie.name,
                        failedCondition = "Bus tie '${tie.busTieId}' references a board id not present in the active project (source=$srcBoardId, target=$dstBoardId)",
                        issueCode = "BUS_TIE_UNRESOLVED_BOARD_REF",
                        severity = SldValidationStatus.INVALID,
                        recommendedCorrection = "Select existing switchboards on both sides of the tie in Power Systems"
                    )
                )
                return@forEach
            }
            val tieNodeId = "tie_${tie.busTieId}"
            val srcNodeId = "circ_$srcBoardId"
            val dstNodeId = "circ_$dstBoardId"
            // P0-4: tie voltage derives from the two endpoint boards (CircuitEntity.voltageV) —
            // never a "400 V" literal. Endpoint disagreement is surfaced, not papered over.
            val srcTieVoltage = projCircuits.firstOrNull { it.id.toLong() == srcBoardId }?.voltageV?.takeIf { it > 0 }
            val dstTieVoltage = projCircuits.firstOrNull { it.id.toLong() == dstBoardId }?.voltageV?.takeIf { it > 0 }
            val tieVoltageStr = when {
                srcTieVoltage != null && dstTieVoltage != null && srcTieVoltage == dstTieVoltage -> "$srcTieVoltage V"
                srcTieVoltage != null && dstTieVoltage != null -> "$srcTieVoltage V / $dstTieVoltage V"
                srcTieVoltage != null -> "$srcTieVoltage V"
                dstTieVoltage != null -> "$dstTieVoltage V"
                else -> "DATA REQUIRED"
            }
            nodes.add(
                SldNode(
                    id = tieNodeId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.BUS_TIE,
                    displayName = tie.name,
                    voltage = tieVoltageStr,
                    dataBlock = SldDataBlock(
                        deviceTypeAndRating = tie.breakerRatingA?.takeIf { it > 0.0 }?.let { "%.0f A".format(it) },
                        statusText = "BUS TIE (${tie.interlockScheme}) — ${tie.normalStatus}"
                    ),
                    status = SldValidationStatus.VALID,
                    provenance = SldProvenance.PROJECT_INPUT,
                    targetTab = 3
                )
            )
            edges.add(
                SldEdge(
                    id = "edge_${srcNodeId}_$tieNodeId",
                    fromNodeId = srcNodeId,
                    toNodeId = tieNodeId,
                    type = SldEdgeType.BUS_TIE_LINK,
                    label = tie.busTieId
                )
            )
            edges.add(
                SldEdge(
                    id = "edge_${tieNodeId}_$dstNodeId",
                    fromNodeId = tieNodeId,
                    toNodeId = dstNodeId,
                    type = SldEdgeType.BUS_TIE_LINK,
                    label = tie.busTieId
                )
            )
        }

        // 2.5 Multi-Instance UPS Processing
        val projUpsList = upsList?.filter {
            it.projectId == effectiveProjectId ||
                (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT")
        } ?: emptyList()

        val effectiveUpsResults: Map<String, UpsResult> = upsResults

        projUpsList.forEach { ups ->
            val upsId = ups.upsId.ifBlank { "1" }
            val upsNodeId = "ups_$upsId"
            val upsRes = effectiveUpsResults[upsId]
            val upsRatedKva = upsRes?.recommendedSize?.takeIf { it > 0.0 } ?: upsRes?.designUpsKVA?.takeIf { it > 0.0 } ?: ups.manualSizeKVA ?: 0.0
            val upsRatedKvaStr = if (upsRatedKva > 0.0) "${upsRatedKva.toInt()} kVA" else "DATA REQUIRED"
            // P0-4: UpsEntity.systemVoltage is the authoritative stored value; missing ⇒
            // explicit DATA REQUIRED (label) and no fabricated current basis.
            val upsVoltageV: Int? = ups.systemVoltage.takeIf { it > 0 }
            val upsSysV = upsVoltageV?.let { "$it V" } ?: "DATA REQUIRED"
            val upsIbAmp = if (upsRatedKva > 0.0 && upsVoltageV != null) {
                "%.0f A".format((upsRatedKva * 1000) / (Math.sqrt(3.0) * upsVoltageV.toDouble()))
            } else "DATA REQUIRED"
            val upsName = ups.upsName.ifBlank { "UPS $upsId" }

            // P0-7: unique exact matches only — no instance #1 of many.
            val fedCircuit = projCircuits.filter {
                it.assignedUpsId.equals(upsId, ignoreCase = true) ||
                    it.fedFrom.trim().equals(upsName, ignoreCase = true)
            }.singleOrNull()
            val fedParentName = fedCircuit?.fedFrom?.trim()?.lowercase()
            val upstreamPanel = if (!fedParentName.isNullOrBlank()) {
                projCircuits.singleOrNull { it.circuitName.trim().lowercase() == fedParentName }
            } else null

            val matchedAts = if (!fedParentName.isNullOrBlank()) {
                projAtsList.singleOrNull { it.atsName.trim().lowercase() == fedParentName || it.atsId.trim().lowercase() == fedParentName }
            } else null

            val matchedTx = if (!fedParentName.isNullOrBlank()) {
                projTransformers.singleOrNull { it.transformerId.trim().lowercase() == fedParentName }
            } else null

            // Explicit relationships first; last-resort sources only when each candidate set
            // holds EXACTLY ONE member (0 or ≥2 ⇒ unresolved ⇒ DATA_REQUIRED status below).
            val fallbackPanel = nodes.filter { it.type == SldNodeType.MDB }.singleOrNull()?.id
            val fallbackAts = projAtsList.singleOrNull()?.let { "ats_${it.atsId.ifBlank { "1" }}" }
            val fallbackTx = projTransformers.singleOrNull()?.let { "tx_${it.transformerId}" }
            val fallbackGrid = nodes.filter { it.type == SldNodeType.UTILITY_SOURCE }.singleOrNull()?.id

            val upstreamSourceId = when {
                upstreamPanel != null -> "circ_${upstreamPanel.id}"
                matchedAts != null -> "ats_${matchedAts.atsId.ifBlank { "1" }}"
                matchedTx != null -> "tx_${matchedTx.transformerId}"
                fallbackPanel != null -> fallbackPanel
                fallbackAts != null -> fallbackAts
                fallbackTx != null -> fallbackTx
                fallbackGrid != null -> fallbackGrid
                else -> null
            }

            // Check if UPS has downstream served circuits
            val hasUpsDest = projCircuits.any {
                it.assignedUpsId == upsId ||
                it.upsBacked ||
                it.fedFrom.trim().equals(upsName, ignoreCase = true) ||
                it.fedFrom.trim().lowercase().contains("ups")
            }

            val upsStatus = if (upstreamSourceId == null || !hasUpsDest) SldValidationStatus.DATA_REQUIRED else SldValidationStatus.VALID

            nodes.add(
                SldNode(
                    id = upsNodeId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.UPS,
                    displayName = "$upsName ($upsRatedKvaStr)",
                    voltage = upsSysV,
                    parentSourceId = upstreamSourceId,
                    dataBlock = SldDataBlock(
                        ratedKva = upsRatedKvaStr,
                        voltage = upsSysV,
                        ibAmp = upsIbAmp,
                        upsBatteryAh = upsRes?.batteryAh?.takeIf { it > 0.0 }?.let { "%.0f Ah".format(it) },
                        upsBatteryStrings = upsRes?.estimatedBatteryStrings?.takeIf { it > 0 }?.let { "$it str" },
                        voltageFrequencyHz = "${ups.systemFrequency} Hz",
                        statusText = upsRes?.status?.ifBlank { "UNINTERRUPTIBLE POWER" } ?: "UNINTERRUPTIBLE POWER"
                    ),
                    status = upsStatus,
                    provenance = SldProvenance.PROJECT_INPUT,
                    targetTab = 3
                )
            )

            if (upstreamSourceId != null) {
                edges.add(
                    SldEdge(
                        id = "edge_${upstreamSourceId}_$upsNodeId",
                        fromNodeId = upstreamSourceId,
                        toNodeId = upsNodeId,
                        type = SldEdgeType.UPS_INPUT,
                        label = "UPS FEED"
                    )
                )
            } else {
                edges.add(
                    SldEdge(
                        id = "edge_unresolved_ups_$upsNodeId",
                        fromNodeId = "unresolved_upstream_$upsNodeId",
                        toNodeId = upsNodeId,
                        type = SldEdgeType.UNRESOLVED_PATH,
                        label = "NO UPSTREAM SOURCE"
                    )
                )
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = upsNodeId,
                        componentName = upsName,
                        failedCondition = "UPS Upstream Power Source Missing",
                        issueCode = "UPS_UPSTREAM_SOURCE_REQUIRED",
                        severity = SldValidationStatus.DATA_REQUIRED,
                        recommendedCorrection = "Ensure ATS or upstream distribution board feeds this UPS"
                    )
                )
            }

            if (!hasUpsDest) {
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = upsNodeId,
                        componentName = upsName,
                        failedCondition = "UPS Output Load Destination Missing",
                        issueCode = "UPS_OUTPUT_DESTINATION_REQUIRED",
                        severity = SldValidationStatus.DATA_REQUIRED,
                        recommendedCorrection = "Assign critical circuits or essential DB to this UPS"
                    )
                )
            }
        }

        // 3. Circuits & Panels
        val effectiveParents = circuitParents.ifEmpty {
            projCircuits.filter { it.parentId != null }.associate { it.id to it.parentId!! }
        }
        val circuitMapById = projCircuits.associateBy { it.id }
        val circuitMapByName = projCircuits.groupBy { it.circuitName.trim().lowercase() }
        val parentFedNames = projCircuits.map { it.fedFrom.trim().lowercase() }.toSet()

        // Circular path validation in parent links
        val visited = mutableSetOf<Int>()
        val inStack = mutableSetOf<Int>()
        fun checkCycle(currId: Int): Boolean {
            visited.add(currId)
            inStack.add(currId)
            val parentId = effectiveParents[currId]
            if (parentId != null && parentId != currId) {
                if (inStack.contains(parentId)) {
                    return true
                }
                if (!visited.contains(parentId) && checkCycle(parentId)) {
                    return true
                }
            }
            inStack.remove(currId)
            return false
        }
        effectiveParents.keys.forEach { cId ->
            if (!visited.contains(cId)) {
                if (checkCycle(cId)) {
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = "circ_$cId",
                            componentName = circuitMapById[cId]?.circuitName ?: "Circuit $cId",
                            failedCondition = "Circular Parent Network Path Detected",
                            issueCode = "CIRCULAR_PATH",
                            severity = SldValidationStatus.INVALID,
                            recommendedCorrection = "Remove loop in circuit parent hierarchy"
                        )
                    )
                }
            }
        }

        // Phase 2A: shared memo for board roll-ups — sub-board results are
        // path-independent, so one cache per buildGraph invocation keeps wide
        // board hierarchies O(N) (verified by the 10k-circuit stress test).
        val boardRollupMemo = HashMap<String, com.example.engine.PanelScheduleRecursiveResult>()

        projCircuits.forEach { circ ->
            val circNodeId = "circ_${circ.id}"
            val isParentBoard = parentFedNames.contains(circ.circuitName.trim().lowercase()) ||
                    effectiveParents.values.contains(circ.id)

            val fedLower = circ.fedFrom.trim().lowercase()
            val sourceKeywords = setOf("transformer", "source", "grid", "main", "utility", "ats", "generator", "gen", "")
            val isExplicitSourceKeyword = sourceKeywords.contains(fedLower) ||
                    fedLower.startsWith("trans") || fedLower.startsWith("tx") ||
                    fedLower.startsWith("ats") || fedLower.startsWith("gen") ||
                    fedLower.startsWith("grid") || fedLower.startsWith("util")

            val nameLower = circ.circuitName.trim().lowercase()
            val nodeType = when {
                circ.panelType?.lowercase()?.contains("mcc") == true -> SldNodeType.MCC
                circ.panelType?.lowercase()?.contains("smdb") == true -> SldNodeType.SMDB
                circ.panelType?.lowercase()?.contains("mdb") == true -> SldNodeType.MDB
                circ.panelType?.lowercase()?.contains("db") == true -> SldNodeType.DB
                nameLower.contains("mcc") -> SldNodeType.MCC
                nameLower.contains("smdb") -> SldNodeType.SMDB
                nameLower.contains("mdb") -> SldNodeType.MDB
                nameLower.contains("db") -> SldNodeType.DB
                isParentBoard && isExplicitSourceKeyword -> SldNodeType.MDB
                isParentBoard -> SldNodeType.SMDB
                else -> SldNodeType.FINAL_CIRCUIT
            }

            val sizingRes = sizingResults[circ.id]
            val scRes = shortCircuitResults[circ.id]
            val cbDesc = if (sizingRes?.deviceType != null && sizingRes?.deviceSize_A != null) {
                "${sizingRes.deviceType} ${sizingRes.deviceSize_A.toInt()}A"
            } else if (circ.overrideDeviceSize != null) {
                "${circ.overrideDeviceType ?: "MCCB"} ${circ.overrideDeviceSize.toInt()}A"
            } else "${circ.loadKVA.toInt()} kVA"

            val cableDesc = sizingRes?.cableSize_mm2 ?: circ.overrideCableSize ?: circ.cableType
            // P0-5: no fabricated Ib. SizingResult.Ib_A is the only authoritative source;
            // when it is absent (including when sizing ran without a usable design current)
            // the node reports DATA REQUIRED instead of a 1.44-assumption estimate.
            val ibStr = sizingRes?.Ib_A?.let { "%.1f A".format(it) } ?: "DATA REQUIRED"
            val izStr = sizingRes?.capacity_derated_Iz_A?.let { "%.1f A".format(it) } ?: if (sizingRes == null) "DATA REQUIRED" else ""
            val vdStr = sizingRes?.vdPct?.let { "%.2f%%".format(it) } ?: if (sizingRes == null) "DATA REQUIRED" else ""
            val cumVdStr = sizingRes?.cumulativeVDpct?.let { "%.2f%%".format(it) } ?: ""
            val ik3Str = scRes?.iscKA?.let { "%.2f kA".format(it) } ?: ""

            var resolvedParentNodeId: String? = null
            var provenance = SldProvenance.PROJECT_INPUT
            var isDisconnected = false

            if (circ.circuitName.equals(circ.fedFrom, ignoreCase = true)) {
                isDisconnected = true
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = circNodeId,
                        componentName = circ.circuitName,
                        failedCondition = "Self-Referencing Parent Assignment",
                        issueCode = "SELF_PARENT",
                        severity = SldValidationStatus.INVALID,
                        recommendedCorrection = "Change fedFrom to a valid parent board or source"
                    )
                )
            } else {
                val parentIdFromMap = effectiveParents[circ.id]
                val parentByMap = if (parentIdFromMap != null) circuitMapById[parentIdFromMap] else null
                // P0-7: duplicate board names are ambiguous — require a unique non-self match.
                val sameNameParents = if (!isExplicitSourceKeyword) circuitMapByName[fedLower]?.filter { it.id != circ.id } else null
                val parentByName = sameNameParents?.singleOrNull()
                val parentCirc = parentByMap ?: parentByName

                // Determine resolvedParentNodeId (using outer scope variables)

                // Determine resolvedParentNodeId (using multi-instance ATS / Generator mappings)
                // P0-7 (E-07): explicit IDs resolve on the unique per-project key; the
                // fallback matches require a UNIQUE result — two ATS/Gen/UPS instances claiming
                // the same output board or name are ambiguous (never instance #1).
                val matchedAts = if (!circ.assignedAtsId.isNullOrBlank()) {
                    projAtsList.singleOrNull { it.atsId.equals(circ.assignedAtsId, ignoreCase = true) }
                } else {
                    projAtsList.singleOrNull { it.outputBoardId == circ.id || it.atsName.equals(circ.fedFrom.trim(), ignoreCase = true) || it.atsId.equals(circ.fedFrom.trim(), ignoreCase = true) }
                }

                val matchedGen = if (!circ.assignedGeneratorId.isNullOrBlank()) {
                    projGenerators.singleOrNull { it.generatorId.equals(circ.assignedGeneratorId, ignoreCase = true) }
                } else {
                    projGenerators.singleOrNull { it.generatorName.equals(circ.fedFrom.trim(), ignoreCase = true) || it.generatorId.equals(circ.fedFrom.trim(), ignoreCase = true) }
                }

                val matchedUps = if (!circ.assignedUpsId.isNullOrBlank()) {
                    projUpsList.singleOrNull { it.upsId.equals(circ.assignedUpsId, ignoreCase = true) }
                } else {
                    projUpsList.singleOrNull { it.upsName.equals(circ.fedFrom.trim(), ignoreCase = true) || it.upsId.equals(circ.fedFrom.trim(), ignoreCase = true) }
                }

                if (circ.upsBacked && circ.assignedUpsId.isNullOrBlank() && matchedUps == null) {
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = circNodeId,
                            componentName = circ.circuitName,
                            failedCondition = "Circuit is marked as UPS-backed but is not explicitly assigned to any UPS instance",
                            issueCode = "UNASSIGNED_UPS_CIRCUIT",
                            severity = SldValidationStatus.WARNING,
                            recommendedCorrection = "Explicitly assign a UPS instance in the circuit editor"
                        )
                    )
                }
                if (circ.atsIncluded && circ.assignedAtsId.isNullOrBlank() && matchedAts == null) {
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = circNodeId,
                            componentName = circ.circuitName,
                            failedCondition = "Circuit is marked as ATS-included but is not explicitly assigned to any ATS instance",
                            issueCode = "UNASSIGNED_ATS_CIRCUIT",
                            severity = SldValidationStatus.WARNING,
                            recommendedCorrection = "Explicitly assign an ATS instance in the circuit editor"
                        )
                    )
                }
                if (circ.generatorIncluded && circ.assignedGeneratorId.isNullOrBlank() && circ.assignedAtsId.isNullOrBlank() && matchedGen == null) {
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = circNodeId,
                            componentName = circ.circuitName,
                            failedCondition = "Circuit is marked as generator-included but is not explicitly assigned to any generator or emergency ATS",
                            issueCode = "UNASSIGNED_GENERATOR_CIRCUIT",
                            severity = SldValidationStatus.WARNING,
                            recommendedCorrection = "Explicitly assign a generator or ATS instance in the circuit editor"
                        )
                    )
                }

                if (matchedUps != null) {
                    resolvedParentNodeId = "ups_${matchedUps.upsId.ifBlank { "1" }}"
                    provenance = SldProvenance.APPROVED_STANDARD
                } else if (matchedAts != null) {
                    // Option (b): When assignedAtsId (or outputBoardId) is set, ATS path takes precedence on SLD diagram
                    // even if assignedGeneratorId is also set (which feeds the ATS emergency input upstream).
                    resolvedParentNodeId = "ats_${matchedAts.atsId.ifBlank { "1" }}"
                    provenance = SldProvenance.APPROVED_STANDARD
                } else if (matchedGen != null && !circ.assignedGeneratorId.isNullOrBlank()) {
                    // Direct Generator Feed (No ATS assigned)
                    resolvedParentNodeId = "gen_${matchedGen.generatorId.ifBlank { "1" }}"
                    provenance = SldProvenance.APPROVED_STANDARD
                } else if (parentCirc != null) {
                    resolvedParentNodeId = "circ_${parentCirc.id}"
                    provenance = SldProvenance.VERIFIED_FORMULA
                } else if (!isExplicitSourceKeyword) {
                    isDisconnected = true
                    val isAmbiguousName = sameNameParents != null && sameNameParents.size > 1
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = circNodeId,
                            componentName = circ.circuitName,
                            failedCondition = if (isAmbiguousName)
                                "Ambiguous parent equipment '$fedLower': ${sameNameParents.size} circuits share this name"
                            else "Parent equipment '$fedLower' not found in project network",
                            issueCode = if (isAmbiguousName) "AMBIGUOUS_SOURCE_INSTANCE" else "MISSING_PARENT",
                            severity = SldValidationStatus.DISCONNECTED,
                            recommendedCorrection = if (isAmbiguousName)
                                "Rename duplicate boards or use an explicit parent relationship"
                            else "Verify fedFrom board name"
                        )
                    )
                } else {
                    // fedFrom is a source keyword (Transformer / Utility / Grid / ATS / Generator)
                    // P0-7: resolve only via a UNIQUE candidate. A generic keyword must never
                    // silently select instance #1 of many — ambiguity becomes an explicit
                    // AMBIGUOUS_SOURCE_INSTANCE issue (disconnected), no match becomes
                    // MISSING_PARENT (disconnected).
                    fun resolveUpstreamSourceNodes(): Pair<String?, SldValidationIssue?> {
                        val txNodes = nodes.filter { it.type == SldNodeType.TRANSFORMER }
                        val gridNodes = nodes.filter { it.type == SldNodeType.UTILITY_SOURCE }
                        return when {
                            txNodes.size == 1 -> txNodes.single().id to null
                            txNodes.isEmpty() && gridNodes.size == 1 -> gridNodes.single().id to null
                            txNodes.size > 1 || gridNodes.size > 1 ->
                                null to SldValidationIssue(
                                    affectedNodeId = circNodeId,
                                    componentName = circ.circuitName,
                                    failedCondition = "Ambiguous upstream source for '$fedLower': multiple source instances exist and no explicit transformerId is assigned",
                                    issueCode = "AMBIGUOUS_SOURCE_INSTANCE",
                                    severity = SldValidationStatus.INVALID,
                                    recommendedCorrection = "Assign an explicit transformerId or a unique source name"
                                )
                            else ->
                                null to SldValidationIssue(
                                    affectedNodeId = circNodeId,
                                    componentName = circ.circuitName,
                                    failedCondition = "No upstream source instance matches '$fedLower' in project network",
                                    issueCode = "MISSING_PARENT",
                                    severity = SldValidationStatus.DISCONNECTED,
                                    recommendedCorrection = "Configure transformer/utility source in project"
                                )
                        }
                    }

                    if (fedLower.contains("ats")) {
                        val atsNameMatches = projAtsList.filter {
                            (it.atsName.isNotBlank() && fedLower.contains(it.atsName.trim().lowercase())) ||
                                (it.atsId.isNotBlank() && fedLower == it.atsId.trim().lowercase())
                        }
                        val fallbackAts = when {
                            atsNameMatches.size == 1 -> atsNameMatches.single()
                            atsNameMatches.isEmpty() && projAtsList.size == 1 -> projAtsList.single()
                            else -> null
                        }
                        when {
                            fallbackAts != null -> {
                                resolvedParentNodeId = "ats_${fallbackAts.atsId.ifBlank { "1" }}"
                                provenance = SldProvenance.APPROVED_STANDARD
                            }
                            projAtsList.isEmpty() -> {
                                // No ATS exists in the project at all — the keyword is generic.
                                val (upstreamId, upstreamIssue) = resolveUpstreamSourceNodes()
                                resolvedParentNodeId = upstreamId
                                if (upstreamId != null) {
                                    provenance = SldProvenance.APPROVED_STANDARD
                                } else {
                                    isDisconnected = true
                                    upstreamIssue?.let { validationIssues.add(it) }
                                }
                            }
                            else -> {
                                // P0-7: ≥2 ATS instances claim this reference (or a generic
                                // keyword with multiple instances) — ambiguous, never #1.
                                isDisconnected = true
                                validationIssues.add(
                                    SldValidationIssue(
                                        affectedNodeId = circNodeId,
                                        componentName = circ.circuitName,
                                        failedCondition = "Ambiguous ATS reference '$fedLower': ${atsNameMatches.size} matching / ${projAtsList.size} total ATS instances exist",
                                        issueCode = "AMBIGUOUS_SOURCE_INSTANCE",
                                        severity = SldValidationStatus.INVALID,
                                        recommendedCorrection = "Assign a unique ATS name/id or use assignedAtsId"
                                    )
                                )
                            }
                        }
                    } else if (fedLower.contains("generator") || fedLower.contains("gen")) {
                        val genNameMatches = projGenerators.filter {
                            (it.generatorName.isNotBlank() && fedLower.contains(it.generatorName.trim().lowercase())) ||
                                (it.generatorId.isNotBlank() && fedLower == it.generatorId.trim().lowercase())
                        }
                        val fallbackGen = when {
                            genNameMatches.size == 1 -> genNameMatches.single()
                            genNameMatches.isEmpty() && projGenerators.size == 1 -> projGenerators.single()
                            else -> null
                        }
                        when {
                            fallbackGen != null -> {
                                resolvedParentNodeId = "gen_${fallbackGen.generatorId.ifBlank { "1" }}"
                                provenance = SldProvenance.APPROVED_STANDARD
                            }
                            projGenerators.isEmpty() -> {
                                val (upstreamId, upstreamIssue) = resolveUpstreamSourceNodes()
                                resolvedParentNodeId = upstreamId
                                if (upstreamId != null) {
                                    provenance = SldProvenance.APPROVED_STANDARD
                                } else {
                                    isDisconnected = true
                                    upstreamIssue?.let { validationIssues.add(it) }
                                }
                            }
                            else -> {
                                isDisconnected = true
                                validationIssues.add(
                                    SldValidationIssue(
                                        affectedNodeId = circNodeId,
                                        componentName = circ.circuitName,
                                        failedCondition = "Ambiguous generator reference '$fedLower': ${genNameMatches.size} matching / ${projGenerators.size} total generator instances exist",
                                        issueCode = "AMBIGUOUS_SOURCE_INSTANCE",
                                        severity = SldValidationStatus.INVALID,
                                        recommendedCorrection = "Assign a unique generator name/id or use assignedGeneratorId"
                                    )
                                )
                            }
                        }
                    } else if (!circ.transformerId.isNullOrBlank()) {
                        val matchedTx = txMap[circ.transformerId.lowercase()]
                        val isCrossProject = transformers.any { it.transformerId.equals(circ.transformerId, ignoreCase = true) }
                        if (matchedTx != null) {
                            resolvedParentNodeId = "tx_${matchedTx.transformerId}"
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else if (isCrossProject) {
                            isDisconnected = true
                            validationIssues.add(
                                SldValidationIssue(
                                    affectedNodeId = circNodeId,
                                    componentName = circ.circuitName,
                                    failedCondition = "Transformer '${circ.transformerId}' belongs to another project",
                                    issueCode = "MISSING_SOURCE_TX",
                                    severity = SldValidationStatus.INVALID,
                                    recommendedCorrection = "Assign transformer from current active project"
                                )
                            )
                        } else if (projTransformers.isEmpty() && nodes.any { it.type == SldNodeType.UTILITY_SOURCE }) {
                            resolvedParentNodeId = nodes.find { it.type == SldNodeType.UTILITY_SOURCE }?.id
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else {
                            isDisconnected = true
                            validationIssues.add(
                                SldValidationIssue(
                                    affectedNodeId = circNodeId,
                                    componentName = circ.circuitName,
                                    failedCondition = "Transformer '${circ.transformerId}' not found in active project",
                                    issueCode = "MISSING_SOURCE_TX",
                                    severity = SldValidationStatus.INVALID,
                                    recommendedCorrection = "Assign transformer from current active project"
                                )
                            )
                        }
                    } else {
                        // P0-7 (E-01): generic source keyword with NO transformerId — resolve
                        // ONLY a unique transformer. Multiple transformers are AMBIGUOUS (never
                        // instance #1). Utility fallback likewise requires exactly one node
                        // (0 transformers ⇒ the single grid_default; ≥2 ⇒ ambiguous).
                        val availableTx = projTransformers.singleOrNull()
                        val utilityNodes = nodes.filter { it.type == SldNodeType.UTILITY_SOURCE }
                        when {
                            availableTx != null -> {
                                resolvedParentNodeId = "tx_${availableTx.transformerId}"
                                provenance = SldProvenance.APPROVED_STANDARD
                            }
                            projTransformers.isEmpty() && utilityNodes.size == 1 -> {
                                resolvedParentNodeId = utilityNodes.single().id
                                provenance = SldProvenance.APPROVED_STANDARD
                            }
                            projTransformers.size > 1 || utilityNodes.size > 1 -> {
                                isDisconnected = true
                                validationIssues.add(
                                    SldValidationIssue(
                                        affectedNodeId = circNodeId,
                                        componentName = circ.circuitName,
                                        failedCondition = "Ambiguous upstream source: ${projTransformers.size} transformer(s) / ${utilityNodes.size} source node(s) exist in project and no transformerId is assigned",
                                        issueCode = "AMBIGUOUS_SOURCE_INSTANCE",
                                        severity = SldValidationStatus.INVALID,
                                        recommendedCorrection = "Assign an explicit transformerId to this circuit"
                                    )
                                )
                            }
                            else -> {
                                isDisconnected = true
                                validationIssues.add(
                                    SldValidationIssue(
                                        affectedNodeId = circNodeId,
                                        componentName = circ.circuitName,
                                        failedCondition = "No upstream source instance found for '${circ.fedFrom}'",
                                        issueCode = "MISSING_PARENT",
                                        severity = SldValidationStatus.DISCONNECTED,
                                        recommendedCorrection = "Configure transformer/utility source in project"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val statusVal = when {
                sizingRes == null -> SldValidationStatus.VALID
                sizingRes.status.contains("FAIL") || sizingRes.status.contains("✗") -> SldValidationStatus.INVALID
                sizingRes.status.contains("WARN") || sizingRes.status.contains("!") -> SldValidationStatus.WARNING
                else -> SldValidationStatus.VALID
            }

            val icuStr = sizingRes?.deviceBreakingKA?.takeIf { it > 0.0 }?.let { "%.0f kA".format(it) }

            // 1B: Protection & ADS verdicts — pre-computed, previously dark.
            val protRes = protectionResults[circ.id]
            val adsRes = adsResults[circ.id]

            // Phase 2A & 3B: board-level load roll-up (TCL / TDL / DF / board current)
            // real PanelScheduleEngine + DiversityCalculationEngine, computed for switchboard nodes.
            val boardRollup = if (nodeType in BOARD_ROLLUP_NODE_TYPES) {
                boardRollupMemo.getOrPut("id_${circ.id}") {
                    com.example.engine.PanelScheduleEngine.generateScheduleRecursive(
                        panelName = circ.circuitName,
                        circuits = projCircuits,
                        targetCircuitId = circ.id,
                        memo = boardRollupMemo
                    )
                }
            } else null

            val directChildren = if (nodeType in BOARD_ROLLUP_NODE_TYPES) {
                projCircuits.filter { child ->
                    child.parentId == circ.id || child.fedFrom.trim().equals(circ.circuitName.trim(), ignoreCase = true)
                }
            } else emptyList()

            val subPanelLoads = if (directChildren.isNotEmpty()) {
                directChildren.filter { child ->
                    val pType = child.panelType?.trim()?.uppercase() ?: ""
                    val isExplicitPanel = pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
                    isExplicitPanel || projCircuits.any { it.parentId == child.id || it.fedFrom.trim().equals(child.circuitName.trim(), ignoreCase = true) }
                }.associate { sub ->
                    val subRollup = boardRollupMemo.getOrPut("id_${sub.id}") {
                        com.example.engine.PanelScheduleEngine.generateScheduleRecursive(
                            panelName = sub.circuitName,
                            circuits = projCircuits,
                            targetCircuitId = sub.id,
                            memo = boardRollupMemo
                        )
                    }
                    sub.id to (subRollup.takeIf { !it.hasCircularReference && it.totalPanelLoadKVA > 0.0 }?.totalPanelLoadKVA ?: sub.loadKVA)
                }
            } else emptyMap()

            val boardIsThreePhase = circ.phases.contains("3")
            // P0-5: diversity is only computed from a valid stored voltage; a missing/invalid
            // voltage must not be replaced by a silent 400 V basis (rollup falls back to
            // entity loads instead, which is deterministic project data).
            val diversityRes = if (nodeType in BOARD_ROLLUP_NODE_TYPES && directChildren.isNotEmpty() && circ.voltageV > 0) {
                com.example.engine.DiversityCalculationEngine.calculateBoardDiversity(
                    circuits = directChildren,
                    subPanelLoads = subPanelLoads,
                    boardVoltageV = circ.voltageV.toDouble(),
                    isThreePhase = boardIsThreePhase
                )
            } else null

            val boardTclKva = if (diversityRes != null && diversityRes.totalConnectedKva > 0.0) {
                diversityRes.totalConnectedKva
            } else {
                boardRollup
                    ?.takeIf { !it.hasCircularReference && it.totalPanelLoadKVA > 0.0 }
                    ?.totalPanelLoadKVA ?: 0.0
            }

            val boardDf = if (circ.demandFactor > 0.0 && circ.demandFactor < 1.0) {
                circ.demandFactor
            } else if (diversityRes != null && diversityRes.overallDiversityFactor > 0.0 && diversityRes.overallDiversityFactor < 1.0) {
                diversityRes.overallDiversityFactor
            } else {
                circ.demandFactor
            }

            val boardTdlKva = if (circ.demandFactor > 0.0 && circ.demandFactor < 1.0) {
                boardTclKva * boardDf
            } else if (diversityRes != null && diversityRes.totalDemandKva > 0.0) {
                diversityRes.totalDemandKva
            } else {
                boardTclKva * boardDf
            }

            // P0-5: guard every voltage division — non-finite currents must never reach
            // downstream sizing (BusbarSizingEngine) or be rendered as facts.
            val boardCurrentA = if (boardTdlKva > 0.0 && circ.voltageV > 0) {
                // Real stored board voltage (CircuitEntity.voltageV) — never a hardcoded default.
                val current = if (boardIsThreePhase) boardTdlKva * 1000.0 / (kotlin.math.sqrt(3.0) * circ.voltageV)
                else boardTdlKva * 1000.0 / circ.voltageV
                if (current.isFinite()) current else 0.0
            } else 0.0
            val boardTclStr = if (boardTclKva > 0.0) "%.1f kVA".format(boardTclKva) else null
            val boardTdlStr = if (boardTdlKva > 0.0) "%.1f kVA".format(boardTdlKva) else null
            val boardDfStr = if (boardTclKva > 0.0 && boardDf > 0.0 && boardDf < 1.0) "%.0f%%".format(boardDf * 100.0) else null
            val boardCurrentStr = if (boardCurrentA > 0.0) "%.1f A".format(boardCurrentA) else null
            val boardUnbalStr = boardRollup
                ?.takeIf { !it.hasCircularReference && it.unbalancePct > 0.0 }
                ?.let { "%.1f%%".format(it.unbalancePct) }

            // Phase 3A: Busbar Sizing Engine (IEC 61439-1 / DIN 43671 / IEC 60865)
            // P0-5: only sized from a valid stored voltage; missing voltage → no busbar
            // computation (fields fall back to explicit entity inputs or stay absent).
            val busbarSizing = if (nodeType in BOARD_ROLLUP_NODE_TYPES && circ.voltageV > 0) {
                val scKaAtBoard = scRes?.iscKA ?: 0.0
                val incRating = sizingRes?.deviceSize_A?.takeIf { it > 0.0 } ?: circ.overrideDeviceSize
                com.example.engine.BusbarSizingEngine.sizeBusbar(
                    designCurrentA = boardCurrentA,
                    incomerRatingA = incRating,
                    faultLevelKa = scKaAtBoard,
                    faultDurationS = 1.0,
                    voltageV = circ.voltageV.toDouble(),
                    material = com.example.engine.BusbarMaterial.COPPER,
                    finish = com.example.engine.BusbarFinish.BARE,
                    ipRating = circ.ipRating ?: "IP54",
                    ambientTempC = 35.0,
                    manualRatingA = circ.busbarRatingA,
                    manualScKa = circ.scRatingKA
                )
            } else null

            // 1B + Phase 3A: Board header fields — engine-backed or stored on CircuitEntity
            val mainIncomingStr = when {
                !circ.mainIncomingType.isNullOrBlank() && (circ.mainIncomingCount ?: 0) > 1 ->
                    "${circ.mainIncomingCount}× ${circ.mainIncomingType}"
                !circ.mainIncomingType.isNullOrBlank() -> circ.mainIncomingType
                else -> null
            }
            val busbarRatingStr = busbarSizing?.ratedCurrentA?.let {
                if (it == it.toLong().toDouble()) "${it.toLong()} A" else "$it A"
            } ?: circ.busbarRatingA?.takeIf { it > 0.0 }?.let {
                if (it == it.toLong().toDouble()) "${it.toLong()} A" else "$it A"
            }
            val scRatingStr = busbarSizing?.icwKA?.let {
                if (it == it.toLong().toDouble()) "${it.toLong()} kA" else "$it kA"
            } ?: circ.scRatingKA?.takeIf { it > 0.0 }?.let {
                if (it == it.toLong().toDouble()) "${it.toLong()} kA" else "$it kA"
            }
            val busbarSizeStr = busbarSizing?.profile?.description
            val busbarIpkStr = busbarSizing?.ipkKA?.let { "$it kA" }
            val boardHasHarmonics = circ.isHarmonic || directChildren.any { it.isHarmonic }
            val busbarNeutralStr = if (busbarSizing != null && boardHasHarmonics) {
                "NOT EVALUATED (MANUAL REVIEW)"
            } else {
                busbarSizing?.neutralProfile
            }
            val busbarPeStr = busbarSizing?.peProfile
            val busbarMaterialStr = busbarSizing?.material?.displayName

            // P0-3: voltage label derives from the SAME stored value (CircuitEntity.voltageV)
            // used by the current calculation — never from phase count. Invalid/missing →
            // explicit DATA REQUIRED instead of a 400 V / 230 V guess.
            val circuitVoltageLabel = if (circ.voltageV > 0) {
                "${circ.voltageV} V (${if (boardIsThreePhase) "3Ph" else "1Ph"})"
            } else {
                "DATA REQUIRED"
            }

            val node = SldNode(
                id = circNodeId,
                projectId = effectiveProjectId,
                type = nodeType,
                displayName = circ.circuitName,
                parentSourceId = resolvedParentNodeId,
                voltage = circuitVoltageLabel,
                dataBlock = SldDataBlock(
                    voltage = circuitVoltageLabel,
                    deviceTypeAndRating = cbDesc,
                    cableDescription = cableDesc,
                    parallelRuns = circ.parallelRuns,
                    lengthM = "${circ.length_m.toInt()} m",
                    ibAmp = ibStr,
                    izAmp = izStr,
                    voltageDropPct = vdStr,
                    cumulativeVdPct = cumVdStr,
                    faultLevelKa = ik3Str,
                    interruptingRatingKa = icuStr,
                    cableIzA = izStr,
                    cableIbA = ibStr,
                    cableUtilizationPct = sizingRes?.utilizationPct?.let { "%.1f%%".format(it) },
                    cableNeutralMm2 = sizingRes?.neutral_mm2,
                    cableCpcMm2 = sizingRes?.earthCPC_mm2,
                    cableInstallation = circ.installation.takeIf { it.isNotBlank() },
                    voltageDropLimitPct = if (circ.maxVDpct > 0.0) "%.2f%%".format(circ.maxVDpct * 100.0) else null,
                    busbarRatingA = busbarRatingStr,
                    scRatingKA = scRatingStr,
                    busbarSize = busbarSizeStr,
                    busbarIpkKa = busbarIpkStr,
                    busbarNeutral = busbarNeutralStr,
                    busbarPe = busbarPeStr,
                    busbarMaterial = busbarMaterialStr,
                    ipRating = circ.ipRating?.takeIf { it.isNotBlank() },
                    panelLocation = circ.panelLocation?.takeIf { it.isNotBlank() },
                    numberOfWays = circ.numberOfWays?.takeIf { it > 0 }?.let { "$it" },
                    mainIncoming = mainIncomingStr,
                    protectionOverload = protRes?.overloadStatus?.takeIf { it.isNotBlank() },
                    protectionShortCircuit = protRes?.shortCircuitStatus?.takeIf { it.isNotBlank() },
                    protectionCableWithstand = protRes?.cableWithstandStatus?.takeIf { it.isNotBlank() },
                    protectionSelectivity = protRes?.selectivityStatus?.takeIf { it.isNotBlank() },
                    adsZsOhm = adsRes?.calculatedZs?.takeIf { it > 0.0 }?.let { "%.3f Ω".format(it) },
                    adsMaxZsOhm = adsRes?.maxZsAllowed?.takeIf { it > 0.0 }?.let { "%.3f Ω".format(it) },
                    adsDisconnectionTime = adsRes?.disconnectionTime?.takeIf { it.isNotBlank() },
                    adsStatus = adsRes?.status?.takeIf { it.isNotBlank() },
                    boardTotalConnectedKva = boardTclStr,
                    boardDemandKva = boardTdlStr,
                    boardDemandFactorPct = boardDfStr,
                    boardDesignCurrentA = boardCurrentStr,
                    boardUnbalancePct = boardUnbalStr,
                    statusText = sizingRes?.status ?: "COMPLIANT"
                ),
                status = if (isDisconnected) SldValidationStatus.DISCONNECTED else statusVal,
                provenance = provenance,
                targetTab = 1,
                rawCircuitId = circ.id
            )

            if (isDisconnected || resolvedParentNodeId == null) {
                unconnected.add(node)
            } else {
                nodes.add(node)
                val edgeType = when {
                    resolvedParentNodeId.startsWith("ups_") -> SldEdgeType.UPS_OUTPUT
                    resolvedParentNodeId.startsWith("ats_") -> SldEdgeType.ATS_OUTPUT
                    else -> SldEdgeType.CABLE
                }
                edges.add(
                    SldEdge(
                        id = "edge_${resolvedParentNodeId}_${circNodeId}",
                        fromNodeId = resolvedParentNodeId,
                        toNodeId = circNodeId,
                        type = edgeType,
                        label = circ.circuitName
                    )
                )
            }
        }

        // Unconnected Standalone Generators
        projGenerators.forEach { gen ->
            val genNodeId = "gen_${gen.generatorId.ifBlank { "1" }}"
            val hasDownstream = edges.any { it.fromNodeId == genNodeId }
            if (!hasDownstream) {
                edges.add(
                    SldEdge(
                        id = "edge_unresolved_gen_$genNodeId",
                        fromNodeId = genNodeId,
                        toNodeId = "unresolved_dest_$genNodeId",
                        type = SldEdgeType.UNRESOLVED_PATH,
                        label = "UNASSIGNED GENERATOR OUTPUT"
                    )
                )
            }
        }

        return ElectricalNetworkGraph(
            activeProjectId = effectiveProjectId,
            nodes = nodes.distinctBy { it.id }.toMutableList(),
            edges = edges.distinctBy { Pair(it.fromNodeId, it.toNodeId) }.toMutableList(),
            unconnectedNodes = unconnected.distinctBy { it.id }.toMutableList(),
            validationIssues = validationIssues
        )
    }
}
