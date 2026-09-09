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
        generatorResult: GeneratorResult? = null,
        atsResult: AtsResult? = null,
        atsEntity: AtsEntity? = null,
        upsResult: UpsResult? = null,
        upsEntity: UpsEntity? = null,
        pfCorrectionResults: List<PfCorrectionResult> = emptyList(),
        generators: List<GeneratorEntity>? = null,
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        atsList: List<AtsEntity>? = null,
        atsResults: Map<String, AtsResult> = emptyMap(),
        upsList: List<UpsEntity>? = null,
        upsResults: Map<String, UpsResult> = emptyMap(),
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
                    displayName = "Utility Grid (22 kV)",
                    voltage = "22 kV",
                    dataBlock = SldDataBlock(
                        voltage = "22 kV / 400 V",
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
                val primaryVoltageStr = if (tx.voltage >= 1000) {
                    val kv = tx.voltage / 1000.0
                    if (kv == kv.toLong().toDouble()) "${kv.toLong()} kV" else "$kv kV"
                } else "22 kV"

                nodes.add(
                    SldNode(
                        id = gridNodeId,
                        projectId = effectiveProjectId,
                        type = SldNodeType.UTILITY_SOURCE,
                        displayName = "Utility Grid ($primaryVoltageStr)",
                        voltage = primaryVoltageStr,
                        dataBlock = SldDataBlock(
                            voltage = primaryVoltageStr,
                            statusText = "GRID SOURCE INCOMER"
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
                val secondaryVoltage = 400.0
                val fallbackFlc = (ratedKvaVal * 1000) / (Math.sqrt(3.0) * secondaryVoltage)
                val ibAmpStr = txRes?.flc?.let { "%.0f A".format(it) } ?: "%.0f A".format(fallbackFlc)
                val loadingStr = txRes?.loadingPct?.let { "%.1f%%".format(it) } ?: ""
                val iscKaStr = txRes?.isc3Phase?.let { "%.2f kA".format(it) } ?: ""

                nodes.add(
                    SldNode(
                        id = txNodeId,
                        projectId = effectiveProjectId,
                        type = SldNodeType.TRANSFORMER,
                        displayName = "Tx: ${tx.transformerId}",
                        parentSourceId = gridNodeId,
                        voltage = "$primaryVoltageStr / 400 V",
                        dataBlock = SldDataBlock(
                            ratedKva = "$ratedKvaStr (${tx.txType})",
                            voltage = "$primaryVoltageStr / 400 V",
                            impedancePct = "${tx.percentZ}%",
                            windingConnection = tx.vectorGroup,
                            loadingPct = loadingStr,
                            isc3PhaseKa = iscKaStr,
                            ibAmp = ibAmpStr,
                            transformerZtMOhm = txRes?.zt?.let { "%.1f mΩ".format(it) },
                            transformerIsc1PhaseKa = txRes?.isc1Phase?.let { "%.2f kA".format(it) },
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
        val projGenerators = when {
            generators != null -> {
                generators.filter { it.projectId == effectiveProjectId || (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT") }
            }
            generatorResult != null && ((generatorResult.finalSize ?: 0.0) > 0.0 || generatorResult.status.isNotBlank()) -> {
                listOf(GeneratorEntity(projectId = effectiveProjectId, generatorId = "1", generatorName = "Generator", manualSizeKVA = generatorResult.finalSize))
            }
            else -> emptyList()
        }

        val effectiveGenResults: Map<String, GeneratorResult> = if (generatorResults.isNotEmpty()) {
            generatorResults
        } else if (generatorResult != null) {
            mapOf("1" to generatorResult)
        } else {
            emptyMap()
        }

        projGenerators.forEach { gen ->
            val genId = gen.generatorId.ifBlank { "1" }
            val genNodeId = "gen_$genId"
            val genRes = effectiveGenResults[genId] ?: (if (genId == "1") generatorResult else null)
            val genRatedKva = genRes?.finalSize?.takeIf { it > 0.0 } ?: gen.manualSizeKVA ?: 0.0
            val genRatedKvaStr = if (genRatedKva > 0.0) "${genRatedKva.toInt()} kVA" else "DATA REQUIRED"
            val sysVoltage = 400
            val sysV = 400.0
            val genIbAmp = "%.0f A".format((genRatedKva * 1000) / (Math.sqrt(3.0) * sysV))
            val genLoadingPct = genRes?.loadingPct?.let { "%.1f%%".format(it) } ?: ""
            val genName = gen.generatorName.ifBlank { "Generator $genId" }
            val genStatusText = if (genRes?.status?.isNotBlank() == true) genRes.status else "EMERGENCY GENERATOR"
            // Real output frequency: derive from the ATS whose emergency source is this generator
            // (AtsEntity.systemFrequency is a stored column, default 50). Never fabricated.
            val genFreq = atsList
                ?.filter { it.projectId == effectiveProjectId || it.projectId == "DEFAULT_PROJECT" }
                ?.firstOrNull { it.emergencySourceId?.uppercase() == genId.uppercase() }
                ?.systemFrequency
                ?: atsList?.firstOrNull()?.systemFrequency

            nodes.add(
                SldNode(
                    id = genNodeId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.GENERATOR,
                    displayName = "$genName ($genRatedKvaStr)",
                    voltage = "$sysVoltage V",
                    dataBlock = SldDataBlock(
                        ratedKva = genRatedKvaStr,
                        voltage = "$sysVoltage V",
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
                val groupKey = gen.syncBusId?.takeIf { it.isNotBlank() }
                    ?: gen.parallelingGroupId?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                syncGroups.getOrPut(groupKey) { mutableListOf() }.add(gen)
            }
            syncGroups.forEach { (groupKey, groupGens) ->
                if (groupGens.size < 2) return@forEach
                val syncNodeId = "sync_$groupKey"
                val groupKva = groupGens.sumOf { (it.manualSizeKVA ?: 0.0) }
                val groupKvaStr = if (groupKva > 0.0) " %.0f kVA total".format(groupKva) else ""
                nodes.add(
                    SldNode(
                        id = syncNodeId,
                        projectId = effectiveProjectId,
                        type = SldNodeType.SYNC_BUS,
                        displayName = "Sync Bus $groupKey",
                        voltage = "400 V",
                        dataBlock = SldDataBlock(
                            ratedKva = if (groupKva > 0.0) "${groupKva.toInt()} kVA" else null,
                            voltage = "400 V",
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
        val projAtsList = when {
            atsList != null -> {
                atsList.filter {
                    it.projectId == effectiveProjectId ||
                        (
                            it.projectId == "DEFAULT_PROJECT" &&
                                effectiveProjectId != "DEFAULT_PROJECT"
                        )
                }
            }

            atsEntity != null -> {
                listOf(atsEntity)
            }

            atsResult != null &&
                atsResult.status.isNotBlank() &&
                atsResult.status !in setOf("SELECT_LOADS", "NO_LOADS") -> {
                listOf(
                    AtsEntity(
                        projectId = effectiveProjectId,
                        atsId = "1",
                        atsName = "ATS-01"
                    )
                )
            }

            else -> emptyList()
        }

        val effectiveAtsResults: Map<String, AtsResult> = if (atsResults.isNotEmpty()) {
            atsResults
        } else if (atsResult != null) {
            mapOf((atsEntity?.atsId ?: "1") to atsResult)
        } else {
            emptyMap()
        }

        projAtsList.forEach { ats ->
            val atsId = ats.atsId.ifBlank { "1" }
            val atsNodeId = "ats_$atsId"
            val atsRes = effectiveAtsResults[atsId] ?: (if (atsId == "1" || atsId == atsEntity?.atsId) atsResult else null)
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
                // Unassigned normal source fallback: check for single upstream MDB or single transformer
                val mdbCircuit = projCircuits.firstOrNull { circ ->
                    (atsOutputBoardId == null || circ.id != atsOutputBoardId) &&
                    circ.assignedAtsId != atsId &&
                    ((circ.panelType != null && circ.panelType.lowercase().contains("mdb")) ||
                    circ.circuitName.lowercase().contains("mdb") ||
                    circ.fedFrom.trim().lowercase().startsWith("trans") ||
                    circ.fedFrom.trim().lowercase().startsWith("tx"))
                }
                normalSourceNodeId = if (mdbCircuit != null) {
                    "circ_${mdbCircuit.id}"
                } else if (projTransformers.size == 1) {
                    "tx_${projTransformers.single().transformerId}"
                } else {
                    null
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
            nodes.add(
                SldNode(
                    id = tieNodeId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.BUS_TIE,
                    displayName = tie.name,
                    voltage = "400 V",
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
        val projUpsList = when {
            upsList != null -> {
                upsList.filter {
                    it.projectId == effectiveProjectId ||
                        (it.projectId == "DEFAULT_PROJECT" && effectiveProjectId != "DEFAULT_PROJECT")
                }
            }
            upsEntity != null -> listOf(upsEntity)
            upsResult != null && upsResult.status.isNotBlank() -> {
                listOf(
                    UpsEntity(
                        projectId = effectiveProjectId,
                        upsId = "1",
                        upsName = "UPS 1",
                        manualSizeKVA = upsResult.recommendedSize ?: upsResult.designUpsKVA
                    )
                )
            }
            else -> emptyList()
        }

        val effectiveUpsResults: Map<String, UpsResult> = if (upsResults.isNotEmpty()) {
            upsResults
        } else if (upsResult != null) {
            mapOf((upsEntity?.upsId ?: "1") to upsResult)
        } else {
            emptyMap()
        }

        projUpsList.forEach { ups ->
            val upsId = ups.upsId.ifBlank { "1" }
            val upsNodeId = "ups_$upsId"
            val upsRes = effectiveUpsResults[upsId] ?: (if (upsId == "1" || upsId == upsEntity?.upsId) upsResult else null)
            val upsRatedKva = upsRes?.recommendedSize?.takeIf { it > 0.0 } ?: upsRes?.designUpsKVA?.takeIf { it > 0.0 } ?: ups.manualSizeKVA ?: 0.0
            val upsRatedKvaStr = if (upsRatedKva > 0.0) "${upsRatedKva.toInt()} kVA" else "DATA REQUIRED"
            val upsSysV = if (ups.systemVoltage > 0) "${ups.systemVoltage} V" else "400 V"
            val sysVDbl = (ups.systemVoltage.takeIf { it > 0 } ?: 400).toDouble()
            val upsIbAmp = if (upsRatedKva > 0.0) "%.0f A".format((upsRatedKva * 1000) / (Math.sqrt(3.0) * sysVDbl)) else "DATA REQUIRED"
            val upsName = ups.upsName.ifBlank { "UPS $upsId" }

            // Resolve upstream source for UPS (from assigned panel, ATS output, transformer, or utility)
            val fedCircuit = projCircuits.firstOrNull {
                it.assignedUpsId.equals(upsId, ignoreCase = true) ||
                    it.fedFrom.trim().equals(upsName, ignoreCase = true)
            }
            val fedParentName = fedCircuit?.fedFrom?.trim()?.lowercase()
            val upstreamPanel = if (!fedParentName.isNullOrBlank()) {
                projCircuits.firstOrNull { it.circuitName.trim().lowercase() == fedParentName }
            } else null

            val matchedAts = if (!fedParentName.isNullOrBlank()) {
                projAtsList.firstOrNull { it.atsName.trim().lowercase() == fedParentName || it.atsId.trim().lowercase() == fedParentName }
            } else null

            val matchedTx = if (!fedParentName.isNullOrBlank()) {
                projTransformers.firstOrNull { it.transformerId.trim().lowercase() == fedParentName }
            } else null

            val fallbackPanel = nodes.find { it.type == SldNodeType.MDB }?.id
            val fallbackAts = projAtsList.firstOrNull()?.let { "ats_${it.atsId.ifBlank { "1" }}" }
            val fallbackTx = projTransformers.firstOrNull()?.let { "tx_${it.transformerId}" }
            val fallbackGrid = nodes.find { it.type == SldNodeType.UTILITY_SOURCE }?.id

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
            val ibStr = sizingRes?.Ib_A?.let { "%.1f A".format(it) } ?: if (sizingRes == null) "DATA REQUIRED" else "${(circ.loadKVA * 1.44).toInt()} A"
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
                val parentByName = if (!isExplicitSourceKeyword) circuitMapByName[fedLower]?.firstOrNull { it.id != circ.id } else null
                val parentCirc = parentByMap ?: parentByName

                // Determine resolvedParentNodeId (using outer scope variables)

                // Determine resolvedParentNodeId (using multi-instance ATS / Generator mappings)
                val matchedAts = if (!circ.assignedAtsId.isNullOrBlank()) {
                    projAtsList.firstOrNull { it.atsId.equals(circ.assignedAtsId, ignoreCase = true) }
                } else {
                    projAtsList.firstOrNull { it.outputBoardId == circ.id || it.atsName.equals(circ.fedFrom.trim(), ignoreCase = true) || it.atsId.equals(circ.fedFrom.trim(), ignoreCase = true) }
                }

                val matchedGen = if (!circ.assignedGeneratorId.isNullOrBlank()) {
                    projGenerators.firstOrNull { it.generatorId.equals(circ.assignedGeneratorId, ignoreCase = true) }
                } else {
                    projGenerators.firstOrNull { it.generatorName.equals(circ.fedFrom.trim(), ignoreCase = true) || it.generatorId.equals(circ.fedFrom.trim(), ignoreCase = true) }
                }

                val matchedUps = if (!circ.assignedUpsId.isNullOrBlank()) {
                    projUpsList.firstOrNull { it.upsId.equals(circ.assignedUpsId, ignoreCase = true) }
                } else if (circ.upsBacked) {
                    projUpsList.firstOrNull()
                } else {
                    projUpsList.firstOrNull { it.upsName.equals(circ.fedFrom.trim(), ignoreCase = true) || it.upsId.equals(circ.fedFrom.trim(), ignoreCase = true) }
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
                    validationIssues.add(
                        SldValidationIssue(
                            affectedNodeId = circNodeId,
                            componentName = circ.circuitName,
                            failedCondition = "Parent equipment '$fedLower' not found in project network",
                            issueCode = "MISSING_PARENT",
                            severity = SldValidationStatus.DISCONNECTED,
                            recommendedCorrection = "Verify fedFrom board name"
                        )
                    )
                } else {
                    // fedFrom is a source keyword (Transformer / Utility / Grid / ATS / Generator)
                    if (fedLower.contains("ats")) {
                        val fallbackAts = projAtsList.firstOrNull { fedLower.contains(it.atsName.lowercase()) } ?: projAtsList.firstOrNull()
                        if (fallbackAts != null) {
                            resolvedParentNodeId = "ats_${fallbackAts.atsId.ifBlank { "1" }}"
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else {
                            resolvedParentNodeId = nodes.find { it.type == SldNodeType.TRANSFORMER }?.id ?: nodes.find { it.type == SldNodeType.UTILITY_SOURCE }?.id
                        }
                    } else if (fedLower.contains("generator") || fedLower.contains("gen")) {
                        val fallbackGen = projGenerators.firstOrNull { fedLower.contains(it.generatorName.lowercase()) } ?: projGenerators.firstOrNull()
                        if (fallbackGen != null) {
                            resolvedParentNodeId = "gen_${fallbackGen.generatorId.ifBlank { "1" }}"
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else {
                            resolvedParentNodeId = nodes.find { it.type == SldNodeType.TRANSFORMER }?.id ?: nodes.find { it.type == SldNodeType.UTILITY_SOURCE }?.id
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
                        val availableTx = projTransformers.firstOrNull()
                        if (availableTx != null) {
                            resolvedParentNodeId = "tx_${availableTx.transformerId}"
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else if (nodes.any { it.type == SldNodeType.UTILITY_SOURCE }) {
                            resolvedParentNodeId = nodes.find { it.type == SldNodeType.UTILITY_SOURCE }?.id
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else {
                            isDisconnected = true
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

            val node = SldNode(
                id = circNodeId,
                projectId = effectiveProjectId,
                type = nodeType,
                displayName = circ.circuitName,
                parentSourceId = resolvedParentNodeId,
                voltage = if (circ.phases.contains("3")) "400 V (3Ph)" else "230 V (1Ph)",
                dataBlock = SldDataBlock(
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
