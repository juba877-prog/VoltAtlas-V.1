package com.example.ai.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry holding available deterministic engineering tools for the AI orchestrator.
 */
class EngineeringAiToolRegistry {

    private val tools = ConcurrentHashMap<String, EngineeringAiTool>()

    fun register(tool: EngineeringAiTool) {
        tools[tool.name] = tool
    }

    fun unregister(toolName: String) {
        tools.remove(toolName)
    }

    fun getTool(name: String): EngineeringAiTool? {
        return tools[name]
    }

    fun getAllTools(): List<EngineeringAiTool> {
        return tools.values.toList()
    }

    fun clear() {
        tools.clear()
    }
}
