package com.example.hierarchy

import com.example.data.CircuitEntity
import com.example.data.SourceType

data class HierarchyNode(
    val board: CircuitEntity,
    val children: List<HierarchyNode>
)

data class HierarchyValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

object HierarchyResolver {

    /**
     * Best-effort backfill: Maps legacy `fedFrom` text to `parentId` where an exact
     * board name match exists in the project list.
     */
    fun backfillParents(boards: List<CircuitEntity>): List<CircuitEntity> {
        val nameToIdMap = boards.associateBy { it.circuitName.trim().lowercase() }
        return boards.map { board ->
            if (board.parentId != null) {
                return@map board
            }
            val fedFromClean = board.fedFrom.trim()
            val matchedParent = nameToIdMap[fedFromClean.lowercase()]

            if (matchedParent != null && matchedParent.id != board.id) {
                board.copy(
                    parentId = matchedParent.id,
                    sourceType = SourceType.PANEL.name
                )
            } else {
                val derivedSource = when (fedFromClean.uppercase()) {
                    "GENERATOR" -> SourceType.GENERATOR.name
                    "GRID", "UTILITY GRID" -> SourceType.GRID.name
                    "ATS" -> "ATS" // Special handling for ATS
                    else -> SourceType.TRANSFORMER.name
                }
                board.copy(
                    parentId = null,
                    sourceType = derivedSource
                )
            }
        }
    }

    /**
     * Computes the level (depth in the tree) for every board in the project.
     * Source-fed boards (parentId == null) get level 0; children get parent's level + 1.
     */
    fun computeLevels(boards: List<CircuitEntity>): List<CircuitEntity> {
        val boardMap = boards.associateBy { it.id }
        
        fun calculateBoardLevel(board: CircuitEntity, visited: Set<Int>): Int {
            if (board.parentId == null) return 0
            if (visited.contains(board.id)) return 0 // Cycle safeguard
            val parent = boardMap[board.parentId] ?: return 0
            return 1 + calculateBoardLevel(parent, visited + board.id)
        }

        return boards.map { board ->
            val level = calculateBoardLevel(board, emptySet())
            board.copy(level = level)
        }
    }

    /**
     * Builds a tree / forest of hierarchy nodes for SLD and board tree visualization.
     */
    fun buildTree(boards: List<CircuitEntity>): List<HierarchyNode> {
        val resolvedBoards = computeLevels(boards)
        val childrenMap = resolvedBoards.groupBy { it.parentId }

        fun buildSubtree(board: CircuitEntity): HierarchyNode {
            val children = childrenMap[board.id] ?: emptyList()
            return HierarchyNode(
                board = board,
                children = children.map { buildSubtree(it) }
            )
        }

        val rootBoards = resolvedBoards.filter { it.parentId == null }
        return rootBoards.map { buildSubtree(it) }
    }

    /**
     * Validates board relationships for self-parenting, cycles, non-existent parents, and orphaned nodes.
     */
    fun validate(boards: List<CircuitEntity>): HierarchyValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val boardMap = boards.associateBy { it.id }

        boards.forEach { board ->
            // 1. Self-parenting
            if (board.parentId != null && board.parentId == board.id) {
                errors.add("Board '${board.circuitName}' (ID ${board.id}) cannot be its own parent.")
            }

            // 2. Non-existent parent
            if (board.parentId != null && !boardMap.containsKey(board.parentId)) {
                errors.add("Board '${board.circuitName}' references non-existent parent ID ${board.parentId}.")
            }

            // 3. Cycle detection (ancestor traversal)
            if (board.parentId != null && board.parentId != board.id) {
                var currentId: Int? = board.parentId
                val visited = mutableSetOf(board.id)
                var hasCycle = false

                while (currentId != null && !hasCycle) {
                    if (visited.contains(currentId)) {
                        hasCycle = true
                        val cycleParentName = boardMap[currentId]?.circuitName ?: currentId.toString()
                        errors.add("Circular hierarchy dependency detected between '${board.circuitName}' and '$cycleParentName'.")
                    } else {
                        visited.add(currentId)
                        currentId = boardMap[currentId]?.parentId
                    }
                }
            }

            // 4. Orphan check
            if (board.parentId == null && board.sourceType.isNullOrBlank()) {
                warnings.add("Board '${board.circuitName}' has no parent board assigned and no source type defined.")
            }
        }

        return HierarchyValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
