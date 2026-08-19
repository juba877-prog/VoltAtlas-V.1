package com.example.ai.tools.cable

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationFreshnessProvider
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.freshness.contracts.CalculationState
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.EngineeringRepository
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.EngineeringCalculationCoordinator

/**
 * Deterministic tool for running side-effect-free cable what-if scenarios.
 *
 * Guarantees:
 * 1. Zero Room writes / zero project mutation.
 * 2. Zero revision increment.
 * 3. Zero Coordinator live cache or freshness record replacement.
 * 4. Compares baseline vs scenario with field-level diffs and governing constraint changes.
 * 5. Explicitly reports baseline freshness and comparison validity (Blocker J).
 */
class RunCableWhatIfTool(
    private val repository: EngineeringRepository,
    private val revisionProvider: ProjectRevisionProvider,
    private val freshnessProvider: CalculationFreshnessProvider? = null,
    private val coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator
) : DeterministicCableTool<RunCableWhatIfRequest, CableWhatIfResult> {

    override val toolName = CableToolNames.RUN_WHAT_IF
    override val description = "Evaluates a temporary what-if scenario with field-level overrides without modifying project state."

    override suspend fun execute(
        request: ToolRequestEnvelope<RunCableWhatIfRequest>
    ): ToolResultEnvelope<CableWhatIfResult> {
        val circuitIdInt = request.parameters.circuitId.toIntOrNull()
            ?: return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_CIRCUIT_ID",
                        message = "Circuit ID '${request.parameters.circuitId}' is not a valid integer.",
                        isRecoverable = false
                    )
                )
            )

        val circuit = repository.circuitDao.getCircuitById(circuitIdInt)
            ?: return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                missingDataReason = "Circuit with ID $circuitIdInt was not found."
            )

        if (circuit.projectId != request.projectId) {
            return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                missingDataReason = "Circuit $circuitIdInt does not belong to project '${request.projectId}'."
            )
        }

        // Validate override bounds
        val o = request.parameters.overrides
        if (o.length_m != null && o.length_m <= 0.0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_OVERRIDE_VALUE",
                        failingParameter = "length_m",
                        message = "Override cable length must be greater than zero.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (o.voltageV != null && o.voltageV <= 0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_OVERRIDE_VALUE",
                        failingParameter = "voltageV",
                        message = "Override voltage must be greater than zero.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (o.parallelRuns != null && o.parallelRuns <= 0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_OVERRIDE_VALUE",
                        failingParameter = "parallelRuns",
                        message = "Override parallel runs must be at least 1.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (o.pf != null && (o.pf <= 0.0 || o.pf > 1.0)) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_OVERRIDE_VALUE",
                        failingParameter = "pf",
                        message = "Override power factor must be between 0 and 1.0.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (o.maxVDpct != null && o.maxVDpct <= 0.0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_OVERRIDE_VALUE",
                        failingParameter = "maxVDpct",
                        message = "Override maximum voltage drop percentage limit must be greater than zero.",
                        isRecoverable = true
                    )
                )
            )
        }

        val allCircuits = repository.circuitDao.getCircuitsListForProject(request.projectId)
        val transformers = repository.transformerDao.getTransformersListForProject(request.projectId)
        val ancestors = coordinator.resolveAncestorChain(circuit, allCircuits)

        var parentCumulativeVD = 0.0
        for (ancestor in ancestors) {
            val ancestorResult = coordinator.getCachedSizingResult(request.projectId, ancestor.id)
                ?: CableCalculationService.calculateSingleCircuit(ancestor, parentCumulativeVD, ancestors, transformers)
            parentCumulativeVD = ancestorResult.cumulativeVDpct
        }

        // 1. Baseline calculation & Freshness determination
        val scope = CalculationScope(request.projectId, CalculationEntityType.CIRCUIT, circuitIdInt.toString(), CalculationDomain.CABLE_SIZING)
        val freshnessState = freshnessProvider?.getCalculationState(scope)?.state ?: CalculationState.CURRENT
        val cachedBaseline = coordinator.getCachedSizingResult(request.projectId, circuitIdInt)

        val baselineResult = cachedBaseline ?: CableCalculationService.calculateSingleCircuit(circuit, parentCumulativeVD, ancestors, transformers)
        val baselineInterp = GoverningCriterionInterpreter.interpret(baselineResult, circuit, parentCumulativeVD)

        val (baselineFreshnessStr, comparisonValidityStr) = when {
            cachedBaseline == null -> "MISSING" to "NO_BASELINE_RECORDED"
            freshnessState == CalculationState.STALE -> "STALE" to "LIMITED_BASELINE_STALE"
            else -> "CURRENT" to "FULL_COMPARISON"
        }

        // 2. Track input field differences
        val diffs = mutableListOf<WhatIfFieldDiffDto>()

        fun <T> checkDiff(name: String, base: T, override: T?) {
            if (override != null && override != base) {
                diffs.add(WhatIfFieldDiffDto(name, base.toString(), override.toString()))
            }
        }

        checkDiff("length_m", circuit.length_m, o.length_m)
        checkDiff("loadKVA", circuit.loadKVA, o.loadKVA)
        checkDiff("pf", circuit.pf, o.pf)
        checkDiff("voltageV", circuit.voltageV, o.voltageV)
        checkDiff("maxVDpct", circuit.maxVDpct, o.maxVDpct)
        checkDiff("cableType", circuit.cableType, o.cableType)
        checkDiff("manufacturer", circuit.manufacturer, o.manufacturer)
        checkDiff("installation", circuit.installation, o.installation)
        checkDiff("parallelRuns", circuit.parallelRuns, o.parallelRuns)
        checkDiff("airTempC", circuit.airTempC, o.airTempC)
        checkDiff("gndTempC", circuit.gndTempC, o.gndTempC)
        checkDiff("burialDepth_m", circuit.burialDepth_m, o.burialDepth_m)
        checkDiff("soilRes", circuit.soilRes, o.soilRes)
        checkDiff("formation", circuit.formation, o.formation)
        checkDiff("spacing", circuit.spacing, o.spacing)
        checkDiff("noCircuits", circuit.noCircuits, o.noCircuits)
        checkDiff("overrideCableSize", circuit.overrideCableSize ?: "AUTO", o.overrideCableSize)
        checkDiff("overrideDeviceSize", circuit.overrideDeviceSize?.toString() ?: "AUTO", o.overrideDeviceSize?.toString())
        checkDiff("overrideDeviceType", circuit.overrideDeviceType ?: "AUTO", o.overrideDeviceType)

        // 3. Construct scenario circuit
        val scenarioCircuit = circuit.copy(
            length_m = o.length_m ?: circuit.length_m,
            loadKVA = o.loadKVA ?: circuit.loadKVA,
            pf = o.pf ?: circuit.pf,
            voltageV = o.voltageV ?: circuit.voltageV,
            maxVDpct = o.maxVDpct ?: circuit.maxVDpct,
            cableType = o.cableType ?: circuit.cableType,
            manufacturer = o.manufacturer ?: circuit.manufacturer,
            installation = o.installation ?: circuit.installation,
            parallelRuns = o.parallelRuns ?: circuit.parallelRuns,
            airTempC = o.airTempC ?: circuit.airTempC,
            gndTempC = o.gndTempC ?: circuit.gndTempC,
            burialDepth_m = o.burialDepth_m ?: circuit.burialDepth_m,
            soilRes = o.soilRes ?: circuit.soilRes,
            formation = o.formation ?: circuit.formation,
            spacing = o.spacing ?: circuit.spacing,
            noCircuits = o.noCircuits ?: circuit.noCircuits,
            overrideCableSize = o.overrideCableSize ?: circuit.overrideCableSize,
            overrideDeviceSize = o.overrideDeviceSize ?: circuit.overrideDeviceSize,
            overrideDeviceType = o.overrideDeviceType ?: circuit.overrideDeviceType
        )

        // 4. Calculate scenario result purely in-memory
        val scenarioResult = CableCalculationService.calculateSingleCircuit(
            circuit = scenarioCircuit,
            parentCumulativeVD = parentCumulativeVD,
            upstreamCircuits = ancestors,
            transformers = transformers
        )
        val scenarioInterp = GoverningCriterionInterpreter.interpret(scenarioResult, scenarioCircuit, parentCumulativeVD)
        val rev = revisionProvider.getCurrentRevision(request.projectId).revision

        val resultPayload = CableWhatIfResult(
            projectId = request.projectId,
            circuitId = circuitIdInt.toString(),
            circuitName = circuit.circuitName,
            sourceRevision = rev,
            baselineSize = baselineResult.cableSize_mm2,
            scenarioSize = scenarioResult.cableSize_mm2,
            baselineStatus = baselineInterp.overallStatus,
            scenarioStatus = scenarioInterp.overallStatus,
            baselineGoverningCriterion = baselineInterp.governingCriterion,
            scenarioGoverningCriterion = scenarioInterp.governingCriterion,
            baselineFreshness = baselineFreshnessStr,
            comparisonValidity = comparisonValidityStr,
            inputDifferences = diffs,
            scenarioDetails = scenarioInterp.details,
            scenarioChecks = scenarioInterp.checks
        )

        return ToolResultEnvelope.pass(
            requestId = request.requestId,
            toolName = toolName,
            projectId = request.projectId,
            result = resultPayload,
            projectRevision = rev,
            provenance = ProvenanceMetadata(
                engineName = "CableSizingEngine",
                methodologyReference = "IEC 60364-5-52",
                projectId = request.projectId,
                projectRevision = rev
            )
        )
    }
}
