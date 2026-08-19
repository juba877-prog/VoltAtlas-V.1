package com.example.ai.tools.read

import com.example.ai.context.freshness.contracts.CalculationFreshnessProvider
import com.example.ai.context.provider.ProjectContextProvider
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.catalog.CatalogRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for deterministic read-only AI tools.
 */
class DeterministicReadToolRegistry {

    private val tools = ConcurrentHashMap<String, DeterministicReadTool<*, *>>()

    fun register(tool: DeterministicReadTool<*, *>) {
        tools[tool.toolName] = tool
    }

    fun unregister(toolName: String) {
        tools.remove(toolName)
    }

    @Suppress("UNCHECKED_CAST")
    fun <TReq, TRes> getTool(toolName: String): DeterministicReadTool<TReq, TRes>? {
        return tools[toolName] as? DeterministicReadTool<TReq, TRes>
    }

    fun getAllTools(): List<DeterministicReadTool<*, *>> {
        return tools.values.toList()
    }

    fun containsTool(toolName: String): Boolean = tools.containsKey(toolName)

    fun clear() {
        tools.clear()
    }

    companion object {
        fun createDefault(
            projectContextProvider: ProjectContextProvider,
            freshnessProvider: CalculationFreshnessProvider,
            revisionProvider: ProjectRevisionProvider,
            catalogRepository: CatalogRepository
        ): DeterministicReadToolRegistry {
            val registry = DeterministicReadToolRegistry()
            registry.register(GetCurrentProjectContextTool(projectContextProvider))
            registry.register(GetEntityTool(projectContextProvider))
            registry.register(GetCalculationStateTool(freshnessProvider))
            registry.register(GetDependenciesTool(projectContextProvider))
            registry.register(GetChangeHistoryTool(revisionProvider))
            registry.register(GetCatalogItemTool(catalogRepository))
            return registry
        }
    }
}
