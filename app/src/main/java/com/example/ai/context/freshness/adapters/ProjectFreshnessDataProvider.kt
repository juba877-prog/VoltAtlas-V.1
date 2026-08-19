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
