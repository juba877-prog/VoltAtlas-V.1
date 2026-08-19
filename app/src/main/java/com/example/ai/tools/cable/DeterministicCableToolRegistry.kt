package com.example.ai.tools.cable

import com.example.ai.context.freshness.contracts.CalculationFreshnessProvider
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.EngineeringRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for deterministic cable engineering tools.
 */
class DeterministicCableToolRegistry {

    private val tools = ConcurrentHashMap<String, DeterministicCableTool<*, *>>()

    fun register(tool: DeterministicCableTool<*, *>, allowOverride: Boolean = false) {
        if (!allowOverride && tools.containsKey(tool.toolName)) {
            throw IllegalArgumentException("Cable tool '${tool.toolName}' is already registered.")
        }
        tools[tool.toolName] = tool
    }

    @Suppress("UNCHECKED_CAST")
    fun <TReq, TRes> getTool(toolName: String): DeterministicCableTool<TReq, TRes>? {
        return tools[toolName] as? DeterministicCableTool<TReq, TRes>
    }

    fun getAllToolNames(): Set<String> = tools.keys.toSet()

    fun clear() {
        tools.clear()
    }

    companion object {
        fun createDefault(
            repository: EngineeringRepository,
            revisionProvider: ProjectRevisionProvider,
            freshnessProvider: CalculationFreshnessProvider,
            coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator
        ): DeterministicCableToolRegistry {
            val registry = DeterministicCableToolRegistry()
            registry.register(CalculateCableSizingTool(repository, revisionProvider, freshnessProvider, coordinator))
            registry.register(EvaluateCableCandidateTool(repository, revisionProvider, coordinator))
            registry.register(CompareCableCandidatesTool(repository, revisionProvider, coordinator))
            registry.register(RunCableWhatIfTool(repository, revisionProvider, freshnessProvider, coordinator))
            return registry
        }
    }
}
