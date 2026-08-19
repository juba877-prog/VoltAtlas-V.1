package com.example.ai.tools

import com.example.ai.tools.cable.DeterministicCableTool
import com.example.ai.tools.cable.DeterministicCableToolRegistry
import com.example.ai.tools.read.DeterministicReadTool
import com.example.ai.tools.read.DeterministicReadToolRegistry

/**
 * Unified discovery and dispatch facade for all deterministic engineering AI tools (Read & Cable).
 *
 * Guarantees:
 * 1. Single controlled orchestration surface: Exposes both Phase 5 (Read) and Phase 6 (Cable) tools.
 * 2. Strict namespace uniqueness: Rejects duplicate tool names across domains.
 * 3. Additive bridge: Zero Gemini / UI dependencies.
 */
class UnifiedDeterministicToolRegistry(
    private val readRegistry: DeterministicReadToolRegistry,
    private val cableRegistry: DeterministicCableToolRegistry
) {

    fun getAllToolNames(): Set<String> {
        val readNames = readRegistry.getAllToolNames()
        val cableNames = cableRegistry.getAllToolNames()
        val overlap = readNames.intersect(cableNames)
        require(overlap.isEmpty()) {
            "Tool namespace collision detected between Read and Cable registries: $overlap"
        }
        return readNames + cableNames
    }

    fun isReadTool(toolName: String): Boolean =
        readRegistry.containsTool(toolName)

    fun isCableTool(toolName: String): Boolean =
        cableRegistry.getAllToolNames().contains(toolName)

    @Suppress("UNCHECKED_CAST")
    fun <TReq, TRes> getReadTool(toolName: String): DeterministicReadTool<TReq, TRes>? =
        readRegistry.getTool(toolName)

    @Suppress("UNCHECKED_CAST")
    fun <TReq, TRes> getCableTool(toolName: String): DeterministicCableTool<TReq, TRes>? =
        cableRegistry.getTool(toolName)
}
