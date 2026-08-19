package com.example.ai.tools.read

import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope

/**
 * Strongly typed interface for deterministic, pure read-only AI tools.
 *
 * Invariants:
 * 1. Zero mutation: Tools never insert, update, or delete repository data.
 * 2. Zero computation: Tools never invoke engineering calculation engines to generate new data.
 * 3. Zero fabrication: Missing data returns typed MISSING/INCOMPLETE envelopes.
 * 4. Zero LLM dependencies: Tools execute purely deterministic Kotlin logic without calling Gemini.
 */
interface DeterministicReadTool<TReq, TRes> {
    val toolName: String
    val description: String

    suspend fun execute(request: ToolRequestEnvelope<TReq>): ToolResultEnvelope<TRes>
}
