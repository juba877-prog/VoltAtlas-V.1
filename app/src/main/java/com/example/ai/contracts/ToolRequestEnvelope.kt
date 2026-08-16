package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Generic, typed, versionable request envelope for engineering tool invocation.
 *
 * @param T The typed, serialization-safe parameter structure for the specific tool.
 */
@JsonClass(generateAdapter = true)
data class ToolRequestEnvelope<T>(
    val contractVersion: Int = 1,
    val requestId: String,
    val conversationId: String? = null,
    val projectId: String,
    val projectRevision: Long? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val toolName: String,
    val executionMode: ExecutionMode = ExecutionMode.LIVE_CALCULATION,
    val parameters: T,
    val requestedAt: Long = System.currentTimeMillis(),
    val requestedBy: String = "AI_ORCHESTRATOR"
)
