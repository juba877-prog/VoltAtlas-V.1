package com.example.ai.context.revision

import com.squareup.moshi.JsonClass

/**
 * Persisted snapshot of project revision state.
 */
@JsonClass(generateAdapter = true)
data class ProjectRevisionSnapshot(
    val projectId: String,
    val revision: Long,
    val updatedAt: Long?
)

/**
 * Pure read-only contract providing authoritative persisted revision of a project.
 * Implemented by EngineeringRepository; consumed by future ProjectContextProvider and Freshness evaluator.
 */
interface ProjectRevisionProvider {
    /**
     * Returns the current persisted revision snapshot for the given project.
     * @throws NoSuchElementException if the project does not exist.
     */
    suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot
}
