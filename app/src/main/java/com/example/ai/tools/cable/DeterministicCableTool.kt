package com.example.ai.tools.cable

import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope

/**
 * Strongly typed interface for deterministic cable calculation and evaluation tools.
 */
interface DeterministicCableTool<TReq, TRes> {
    val toolName: String
    val description: String

    suspend fun execute(request: ToolRequestEnvelope<TReq>): ToolResultEnvelope<TRes>
}
