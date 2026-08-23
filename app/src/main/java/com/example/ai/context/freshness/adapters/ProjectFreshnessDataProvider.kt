package com.example.ai.context.freshness.adapters

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity

/**
 * Authoritative read-only data provider required by freshness adapters to resolve current entity graphs.
 */
interface ProjectFreshnessDataProvider {
    suspend fun getCircuits(projectId: String): List<CircuitEntity>
    suspend fun getTransformers(projectId: String): List<TransformerEntity>
}

/**
 * Lightweight in-memory implementation of [ProjectFreshnessDataProvider] backed by circuit and transformer collections.
 */
class InMemoryProjectFreshnessDataProvider(
    val circuits: List<CircuitEntity> = emptyList(),
    val transformers: List<TransformerEntity> = emptyList()
) : ProjectFreshnessDataProvider {

    override suspend fun getCircuits(projectId: String): List<CircuitEntity> {
        val filtered = circuits.filter { it.projectId == projectId }
        return if (filtered.isNotEmpty() || projectId.isEmpty()) filtered else circuits
    }

    override suspend fun getTransformers(projectId: String): List<TransformerEntity> {
        val filtered = transformers.filter { it.projectId == projectId }
        return if (filtered.isNotEmpty() || projectId.isEmpty()) filtered else transformers
    }
}
