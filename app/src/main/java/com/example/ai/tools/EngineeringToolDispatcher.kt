package com.example.ai.tools

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableCandidateSpecDto
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CableWhatIfOverridesDto
import com.example.ai.tools.cable.CableWhatIfResult
import com.example.ai.tools.cable.CalculateCableSizingRequest
import com.example.ai.tools.cable.CandidateSortPolicy
import com.example.ai.tools.cable.CompareCableCandidatesRequest
import com.example.ai.tools.cable.DeterministicCableToolInvoker
import com.example.ai.tools.cable.EvaluateCableCandidateRequest
import com.example.ai.tools.cable.RunCableWhatIfRequest
import com.example.ai.tools.read.CalculationStateResult
import com.example.ai.tools.read.CatalogSearchResult
import com.example.ai.tools.read.ChangeHistoryResult
import com.example.ai.tools.read.DependenciesResult
import com.example.ai.tools.read.DeterministicReadToolInvoker
import com.example.ai.tools.read.EntityContextResult
import com.example.ai.tools.read.GetCalculationStateRequest
import com.example.ai.tools.read.GetCatalogItemRequest
import com.example.ai.tools.read.GetChangeHistoryRequest
import com.example.ai.tools.read.GetDependenciesRequest
import com.example.ai.tools.read.GetEntityRequest
import com.example.ai.tools.read.GetProjectContextRequest
import com.example.ai.tools.read.ProjectContextResult
import com.example.ai.tools.read.ReadToolNames
import com.example.ai.context.freshness.contracts.CalculationDomain
import java.util.UUID

/**
 * Type-erased outcome of a dispatched tool execution.
 *
 * The envelope is strongly typed at the point of creation and erased only here, at the
 * orchestration boundary, where the assistant treats every result uniformly as evidence.
 */
data class ToolExecutionOutcome(
    val toolName: String,
    val requestId: String,
    val envelope: ToolResultEnvelope<*>
)

/**
 * Turns an authorized tool request into a real deterministic execution.
 *
 * Responsibilities:
 * 1. Authorization: reject unknown or disallowed tools before touching the project.
 * 2. Project boundary: the project identity used for execution is ALWAYS the authoritative
 *    one resolved by the runtime; a project identifier supplied by the model is discarded.
 *    A project that cannot be resolved from the revision provider fails closed.
 * 3. Typed construction: model arguments are coerced into the tool's own request contract.
 * 4. Delegation: execution is handed to the existing typed invokers, which supply request
 *    signatures, task-manager integration, provenance and fail-closed error envelopes.
 *
 * This class intentionally contains no engineering logic and no engineering constants.
 */
class EngineeringToolDispatcher(
    private val registry: EngineeringToolRegistry,
    private val readInvoker: DeterministicReadToolInvoker,
    private val cableInvoker: DeterministicCableToolInvoker,
    private val revisionProvider: ProjectRevisionProvider
) {

    /**
     * Executes [toolName] against the authoritative [projectId].
     *
     * Any `projectId`, `entityType` or `entityId` present in [arguments] is overwritten by
     * the authoritative values supplied here, so a model can never retarget execution.
     */
    suspend fun execute(
        toolName: String,
        projectId: String,
        entityType: String?,
        entityId: String?,
        conversationId: String?,
        arguments: Map<String, Any?> = emptyMap(),
        executionMode: ExecutionMode? = null
    ): ToolExecutionOutcome {
        val requestId = "REQ-" + UUID.randomUUID().toString().take(12)

        val descriptor = registry.getDescriptor(toolName)
            ?: return rejected(
                requestId, toolName, projectId, "UNKNOWN_TOOL",
                "Tool '$toolName' is not declared in the authoritative registry."
            )

        val mode = executionMode ?: descriptor.executionMode
        if (!registry.isAllowed(toolName, mode)) {
            return rejected(
                requestId, toolName, projectId, "TOOL_NOT_AUTHORIZED",
                "Tool '$toolName' is not authorized for execution mode $mode."
            )
        }
        if (projectId.isBlank()) {
            return rejected(
                requestId, toolName, projectId, "PROJECT_NOT_FOUND",
                "No authoritative project is active for this request."
            )
        }
        if (descriptor.requiresEntityContext && entityId.isNullOrBlank()) {
            return rejected(
                requestId, toolName, projectId, "MISSING_REQUIRED_INPUT",
                "Tool '$toolName' requires an authorized entity binding."
            )
        }

        // Authoritative revision re-resolution. Fails closed if the project cannot be resolved.
        val revision = try {
            revisionProvider.getCurrentRevision(projectId).revision
        } catch (e: NoSuchElementException) {
            return rejected(
                requestId, toolName, projectId, "PROJECT_NOT_FOUND",
                "Project '$projectId' could not be resolved from authoritative state."
            )
        }

        // Authoritative values always win over anything the model supplied.
        val scopedArguments = arguments.toMutableMap().apply {
            put("projectId", projectId)
            if (entityType != null) put("entityType", entityType)
            if (entityId != null) put("entityId", entityId)
        }

        return try {
            dispatch(
                toolName = toolName,
                requestId = requestId,
                conversationId = conversationId,
                projectId = projectId,
                revision = revision,
                entityType = entityType,
                entityId = entityId,
                arguments = scopedArguments,
                mode = mode
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            ToolExecutionOutcome(
                toolName = toolName,
                requestId = requestId,
                envelope = ToolResultEnvelope.error<Any>(
                    requestId = requestId,
                    toolName = toolName,
                    projectId = projectId,
                    errors = listOf(
                        StructuredError(
                            errorCode = "TOOL_EXECUTION_ERROR",
                            message = "Tool '$toolName' failed during dispatch.",
                            isRecoverable = false
                        )
                    )
                )
            )
        }
    }

    private fun rejected(
        requestId: String,
        toolName: String,
        projectId: String,
        errorCode: String,
        message: String
    ): ToolExecutionOutcome = ToolExecutionOutcome(
        toolName = toolName,
        requestId = requestId,
        envelope = ToolResultEnvelope.notSupported<Any>(
            requestId = requestId,
            toolName = toolName,
            projectId = projectId,
            reason = "$errorCode: $message"
        )
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatch(
        toolName: String,
        requestId: String,
        conversationId: String?,
        projectId: String,
        revision: Long,
        entityType: String?,
        entityId: String?,
        arguments: Map<String, Any?>,
        mode: ExecutionMode
    ): ToolExecutionOutcome {
        val envelope: ToolResultEnvelope<*> = when {
            registry.isReadTool(toolName) -> dispatchRead(
                toolName, requestId, conversationId, projectId, revision, entityType, entityId, arguments, mode
            )

            registry.isCableTool(toolName) -> dispatchCable(
                toolName, requestId, conversationId, projectId, revision, entityType, entityId, arguments, mode
            )

            else -> ToolResultEnvelope.notSupported<Any>(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                reason = "EXECUTOR_NOT_AVAILABLE: no typed implementation is bound to '$toolName'."
            )
        }
        return ToolExecutionOutcome(toolName = toolName, requestId = requestId, envelope = envelope)
    }

    private suspend fun dispatchRead(
        toolName: String,
        requestId: String,
        conversationId: String?,
        projectId: String,
        revision: Long,
        entityType: String?,
        entityId: String?,
        arguments: Map<String, Any?>,
        mode: ExecutionMode
    ): ToolResultEnvelope<*> = when (toolName) {
        ReadToolNames.GET_CURRENT_PROJECT_CONTEXT -> readInvoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                GetProjectContextRequest(
                    projectId = projectId,
                    includeValidation = argBoolean(arguments, "includeValidation") ?: true
                ))
        )

        ReadToolNames.GET_ENTITY -> readInvoker.executeDirect<GetEntityRequest, EntityContextResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                GetEntityRequest(
                    projectId = projectId,
                    entityType = entityType.orEmpty(),
                    entityId = entityId.orEmpty()
                ))
        )

        ReadToolNames.GET_CALCULATION_STATE -> readInvoker.executeDirect<GetCalculationStateRequest, CalculationStateResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                GetCalculationStateRequest(
                    projectId = projectId,
                    entityType = entityType.orEmpty(),
                    entityId = entityId.orEmpty(),
                    domain = argString(arguments, "domain")?.let { requested ->
                        CalculationDomain.entries.firstOrNull { it.name.equals(requested, ignoreCase = true) }
                    }
                ))
        )

        ReadToolNames.GET_DEPENDENCIES -> readInvoker.executeDirect<GetDependenciesRequest, DependenciesResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                GetDependenciesRequest(
                    projectId = projectId,
                    entityType = entityType.orEmpty(),
                    entityId = entityId.orEmpty()
                ))
        )

        ReadToolNames.GET_CHANGE_HISTORY -> readInvoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                GetChangeHistoryRequest(
                    projectId = projectId,
                    entityId = entityId,
                    maxEntries = argInt(arguments, "maxEntries") ?: 10
                ))
        )

        ReadToolNames.GET_CATALOG_ITEM -> readInvoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                GetCatalogItemRequest(
                    category = argString(arguments, "category").orEmpty(),
                    query = argString(arguments, "query"),
                    manufacturer = argString(arguments, "manufacturer"),
                    sizeMm2 = argDouble(arguments, "sizeMm2"),
                    ratingA = argDouble(arguments, "ratingA"),
                    limit = argInt(arguments, "limit") ?: 20
                ))
        )

        else -> ToolResultEnvelope.notSupported<Any>(
            requestId = requestId,
            toolName = toolName,
            projectId = projectId,
            reason = "EXECUTOR_NOT_AVAILABLE: read tool '$toolName' has no dispatch binding."
        )
    }

    private suspend fun dispatchCable(
        toolName: String,
        requestId: String,
        conversationId: String?,
        projectId: String,
        revision: Long,
        entityType: String?,
        entityId: String?,
        arguments: Map<String, Any?>,
        mode: ExecutionMode
    ): ToolResultEnvelope<*> = when (toolName) {
        CableToolNames.CALCULATE_CABLE_SIZING -> cableInvoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                CalculateCableSizingRequest(
                    projectId = projectId,
                    circuitId = entityId.orEmpty(),
                    forceRecalculate = argBoolean(arguments, "forceRecalculate") ?: false
                ))
        )

        CableToolNames.EVALUATE_CABLE_CANDIDATE -> cableInvoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                EvaluateCableCandidateRequest(
                    projectId = projectId,
                    circuitId = entityId.orEmpty(),
                    candidateSizeMm2 = argDouble(arguments, "candidateSizeMm2"),
                    candidateSizeText = argString(arguments, "candidateSizeText"),
                    candidateMaterial = argString(arguments, "candidateMaterial"),
                    candidateParallelRuns = argInt(arguments, "candidateParallelRuns"),
                    candidateProductCode = argString(arguments, "candidateProductCode"),
                    candidateDeviceSizeA = argDouble(arguments, "candidateDeviceSizeA"),
                    candidateDeviceType = argString(arguments, "candidateDeviceType")
                ))
        )

        CableToolNames.COMPARE_CABLE_CANDIDATES -> cableInvoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                CompareCableCandidatesRequest(
                    projectId = projectId,
                    circuitId = entityId.orEmpty(),
                    candidates = argObjectList(arguments, "candidates").map { spec ->
                        CableCandidateSpecDto(
                            candidateId = spec["candidateId"]?.toString(),
                            sizeMm2 = spec["sizeMm2"]?.toString()?.toDoubleOrNull(),
                            sizeText = spec["sizeText"]?.toString(),
                            material = spec["material"]?.toString(),
                            parallelRuns = spec["parallelRuns"]?.toString()?.toIntOrNull(),
                            deviceSizeA = spec["deviceSizeA"]?.toString()?.toDoubleOrNull(),
                            deviceType = spec["deviceType"]?.toString()
                        )
                    },
                    sortPolicy = argString(arguments, "sortPolicy")?.let { requested ->
                        CandidateSortPolicy.entries.firstOrNull { it.name.equals(requested, ignoreCase = true) }
                    } ?: CandidateSortPolicy.INPUT_ORDER
                ))
        )

        CableToolNames.RUN_WHAT_IF -> cableInvoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(
            envelope(requestId, conversationId, projectId, revision, entityType, entityId, toolName, mode,
                RunCableWhatIfRequest(
                    projectId = projectId,
                    circuitId = entityId.orEmpty(),
                    overrides = whatIfOverrides(argObject(arguments, "overrides"))
                ))
        )

        else -> ToolResultEnvelope.notSupported<Any>(
            requestId = requestId,
            toolName = toolName,
            projectId = projectId,
            reason = "EXECUTOR_NOT_AVAILABLE: cable tool '$toolName' has no dispatch binding."
        )
    }

    private fun whatIfOverrides(fields: Map<String, Any?>): CableWhatIfOverridesDto =
        CableWhatIfOverridesDto(
            length_m = fields.doubleOrNull("length_m"),
            loadKVA = fields.doubleOrNull("loadKVA"),
            pf = fields.doubleOrNull("pf"),
            voltageV = fields.intOrNull("voltageV"),
            maxVDpct = fields.doubleOrNull("maxVDpct"),
            cableType = fields.stringOrNull("cableType"),
            manufacturer = fields.stringOrNull("manufacturer"),
            installation = fields.stringOrNull("installation"),
            parallelRuns = fields.intOrNull("parallelRuns"),
            airTempC = fields.intOrNull("airTempC"),
            gndTempC = fields.intOrNull("gndTempC"),
            burialDepth_m = fields.doubleOrNull("burialDepth_m"),
            soilRes = fields.intOrNull("soilRes"),
            formation = fields.stringOrNull("formation"),
            spacing = fields.stringOrNull("spacing"),
            noCircuits = fields.intOrNull("noCircuits"),
            overrideCableSize = fields.stringOrNull("overrideCableSize"),
            overrideDeviceSize = fields.doubleOrNull("overrideDeviceSize"),
            overrideDeviceType = fields.stringOrNull("overrideDeviceType")
        )

    private fun <T> envelope(
        requestId: String,
        conversationId: String?,
        projectId: String,
        revision: Long,
        entityType: String?,
        entityId: String?,
        toolName: String,
        mode: ExecutionMode,
        parameters: T
    ): ToolRequestEnvelope<T> = ToolRequestEnvelope(
        requestId = requestId,
        conversationId = conversationId,
        projectId = projectId,
        projectRevision = revision,
        entityType = entityType,
        entityId = entityId,
        toolName = toolName,
        executionMode = mode,
        parameters = parameters
    )

    private fun argString(arguments: Map<String, Any?>, name: String): String? =
        arguments[name]?.toString()?.takeIf { it.isNotBlank() }

    private fun argDouble(arguments: Map<String, Any?>, name: String): Double? =
        arguments[name]?.toString()?.trim()?.toDoubleOrNull()

    private fun argInt(arguments: Map<String, Any?>, name: String): Int? =
        arguments[name]?.toString()?.trim()?.toDoubleOrNull()?.toInt()

    private fun argBoolean(arguments: Map<String, Any?>, name: String): Boolean? =
        arguments[name]?.toString()?.trim()?.toBooleanStrictOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun argObject(arguments: Map<String, Any?>, name: String): Map<String, Any?> =
        arguments[name] as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun argObjectList(arguments: Map<String, Any?>, name: String): List<Map<String, Any?>> =
        (arguments[name] as? List<Any?>)
            ?.mapNotNull { it as? Map<String, Any?> }
            ?: emptyList()

    private fun Map<String, Any?>.stringOrNull(name: String): String? =
        this[name]?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<String, Any?>.doubleOrNull(name: String): Double? =
        this[name]?.toString()?.trim()?.toDoubleOrNull()

    private fun Map<String, Any?>.intOrNull(name: String): Int? =
        this[name]?.toString()?.trim()?.toDoubleOrNull()?.toInt()
}
