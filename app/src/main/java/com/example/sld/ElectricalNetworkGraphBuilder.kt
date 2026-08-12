package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.data.GeneratorEntity
import com.example.data.AtsEntity
import com.example.data.UpsEntity
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
        pfCorrectionResults: List<PfCorrectionResult> = emptyList()
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
                // FLC is calculated on the LV (secondary) side = 400 V, NOT the primary voltage.
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
                            loadingPct = loadingStr,
                            isc3PhaseKa = iscKaStr,
                            ibAmp = ibAmpStr,
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

        // 2. Generator & ATS processing
        val genRatedKva = generatorResult?.finalSize ?: 0.0
        val hasGen = generatorResult != null && genRatedKva > 0.0
        val genNodeId = "gen_1"
        if (hasGen) {
            val genRatedKvaStr = "${genRatedKva.toInt()} kVA"
            val sysVoltage = atsEntity?.systemVoltage ?: 400
            val sysV = if (sysVoltage > 0) sysVoltage.toDouble() else 400.0
            val genIbAmp = "%.0f A".format((genRatedKva * 1000) / (Math.sqrt(3.0) * sysV))
            val genLoadingPct = "%.1f%%".format(generatorResult.loadingPct)

            nodes.add(
                SldNode(
                    id = genNodeId,
                    projectId = effectiveProjectId,
                    type = SldNodeType.GENERATOR,
                    displayName = "Generator ($genRatedKvaStr)",
                    voltage = "$sysVoltage V",
                    dataBlock = SldDataBlock(
                        ratedKva = genRatedKvaStr,
                        voltage = "$sysVoltage V",
                        ibAmp = genIbAmp,
                        loadingPct = genLoadingPct,
                        statusText = if (generatorResult.status.isNotBlank()) generatorResult.status else "EMERGENCY GENERATOR"
                    ),
                    status = SldValidationStatus.VALID,
                    provenance = SldProvenance.PROJECT_INPUT,
                    targetTab = 3
                )
            )
        }

        val nonNullAtsRes = atsResult
        // NOTE: EMDB / E-DB classification does NOT exist in the Board Type dropdown,
        // so that dead check was removed. ATS output is driven by atsEntity.outputBoardId
        // (the board the user selects on the ATS screen) instead.
        val atsOutputBoardId = atsEntity?.outputBoardId
        val hasEmergencyLoad = projCircuits.any {
            it.atsIncluded ||
            it.generatorIncluded ||
            it.fedFrom.trim().lowercase().contains("ats")
        } || (atsOutputBoardId != null && atsOutputBoardId > 0)
        val hasExplicitAtsResult = nonNullAtsRes != null && nonNullAtsRes.status.isNotBlank() && nonNullAtsRes.status != "SELECT_LOADS"
        val hasAts = (hasGen && hasEmergencyLoad) || hasExplicitAtsResult
        val atsNodeId = "ats_1"
        if (hasAts) {
            val mdbCircuit = projCircuits.firstOrNull { circ ->
                (circ.panelType != null && circ.panelType.lowercase().contains("mdb")) ||
                circ.circuitName.lowercase().contains("mdb") ||
                circ.fedFrom.trim().lowercase().startsWith("trans") ||
                circ.fedFrom.trim().lowercase().startsWith("tx")
            }
            val normalSourceNodeId = if (mdbCircuit != null) "circ_${mdbCircuit.id}" else nodes.find { it.type == SldNodeType.TRANSFORMER }?.id
            val hasNormalSource = normalSourceNodeId != null && projTransformers.isNotEmpty()

            val ratingVal = nonNullAtsRes?.finalRating?.toInt()
            val atsRating = if (ratingVal != null) "${ratingVal}A ATS" else "ATS"
            val atsIbAmp = nonNullAtsRes?.governingCurrent?.let { "%.0f A".format(it) } ?: ""

            // ATS output destination is satisfied either by a board explicitly fedFrom "ATS",
            // OR by the board the user selected as the ATS output board (atsEntity.outputBoardId).
            val hasAtsDest = projCircuits.any { it.fedFrom.trim().lowercase().contains("ats") } ||
                    (atsOutputBoardId != null && projCircuits.any { it.id == atsOutputBoardId })
            val atsStatus = if (!hasNormalSource || !hasGen || !hasAtsDest) {
                SldValidationStatus.DATA_REQUIRED
            } else SldValidationStatus.VALID

            val atsNode = SldNode(
                id = atsNodeId,
                projectId = effectiveProjectId,
                type = SldNodeType.ATS,
                displayName = "ATS-01",
                dataBlock = SldDataBlock(
                    deviceTypeAndRating = atsRating,
                    ibAmp = atsIbAmp,
                    statusText = nonNullAtsRes?.status?.ifBlank { "TRANSFER SWITCH" } ?: "TRANSFER SWITCH"
                ),
                status = atsStatus,
                provenance = SldProvenance.PROJECT_INPUT,
                targetTab = 3
            )
            nodes.add(atsNode)

            if (hasNormalSource) {
                edges.add(
                    SldEdge(
                        id = "edge_${normalSourceNodeId}_ats_1",
                        fromNodeId = normalSourceNodeId,
                        toNodeId = atsNodeId,
                        type = SldEdgeType.ATS_NORMAL_PATH,
                        label = "NORMAL"
                    )
                )
            } else {
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = atsNodeId,
                        componentName = "ATS-01",
                        failedCondition = "ATS Normal Power Source Missing",
                        issueCode = "ATS_NORMAL_SOURCE_REQUIRED",
                        severity = SldValidationStatus.DATA_REQUIRED,
                        recommendedCorrection = "Ensure transformer/utility source is configured and assigned"
                    )
                )
            }

            if (hasGen) {
                edges.add(
                    SldEdge(
                        id = "edge_gen_1_ats_1",
                        fromNodeId = genNodeId,
                        toNodeId = atsNodeId,
                        type = SldEdgeType.ATS_EMERGENCY_PATH,
                        label = "EMERGENCY"
                    )
                )
            } else {
                validationIssues.add(
                    SldValidationIssue(
                        affectedNodeId = atsNodeId,
                        componentName = "ATS-01",
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
                        componentName = "ATS-01",
                        failedCondition = "ATS Output Destination Missing",
                        issueCode = "ATS_OUTPUT_DESTINATION_REQUIRED",
                        severity = SldValidationStatus.DATA_REQUIRED,
                        recommendedCorrection = "Select a downstream output board on the ATS screen, or set a board's Source to ATS"
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
                isParentBoard && circ.fedFrom.lowercase() in listOf("transformer", "ats", "generator", "grid", "source", "main", "") -> SldNodeType.MDB
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
            val fedLower = circ.fedFrom.trim().lowercase()

            val sourceKeywords = setOf("transformer", "source", "grid", "main", "utility", "ats", "generator", "gen", "")
            val isExplicitSourceKeyword = sourceKeywords.contains(fedLower) ||
                    fedLower.startsWith("trans") || fedLower.startsWith("tx") ||
                    fedLower.startsWith("ats") || fedLower.startsWith("gen") ||
                    fedLower.startsWith("grid") || fedLower.startsWith("util")

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

                if (hasAts && circ.id == atsOutputBoardId) {
                    resolvedParentNodeId = atsNodeId
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
                        if (hasAts) {
                            resolvedParentNodeId = atsNodeId
                            provenance = SldProvenance.APPROVED_STANDARD
                        } else {
                            resolvedParentNodeId = nodes.find { it.type == SldNodeType.TRANSFORMER }?.id ?: nodes.find { it.type == SldNodeType.UTILITY_SOURCE }?.id
                        }
                    } else if (fedLower.contains("generator") || fedLower.contains("gen")) {
                        if (hasGen) {
                            resolvedParentNodeId = genNodeId
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
                val edgeType = if (resolvedParentNodeId == atsNodeId) SldEdgeType.ATS_OUTPUT else SldEdgeType.CABLE
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

        return ElectricalNetworkGraph(
            activeProjectId = effectiveProjectId,
            nodes = nodes.distinctBy { it.id }.toMutableList(),
            edges = edges.distinctBy { Pair(it.fromNodeId, it.toNodeId) }.toMutableList(),
            unconnectedNodes = unconnected.distinctBy { it.id }.toMutableList(),
            validationIssues = validationIssues
        )
    }
}
